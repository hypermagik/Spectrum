#include "ShiftDecimatorMultiQueue.h"
#include "vulkan/Utils.h"

namespace Vulkan::DSP {
    std::unique_ptr<ShiftDecimatorMultiQueue> ShiftDecimatorMultiQueue::create(Context *context, Taps &&taps) {
        if (context == nullptr) {
            return nullptr;
        }
        auto processor = std::make_unique<ShiftDecimatorMultiQueue>(context);
        const bool success = processor->initialize(taps);
        return success ? std::move(processor) : nullptr;
    }

    bool ShiftDecimatorMultiQueue::initialize(Taps &taps) {
        for (size_t i = 0; i < taps.size(); i++) {
            auto buffer = Buffer::create(
                    context, F2B(taps[i].size()),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            VK_CHECK(buffer != nullptr);
            buffer->copyFrom(taps[i].data(), 0, F2B(taps[i].size()));
            tapBuffers.emplace_back(std::move(buffer));

            buffer = Buffer::create(
                    context, S2B(taps[i].size() - 1 + (MAX_SAMPLE_ARRAY_SIZE >> i)),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VK_CHECK(buffer != nullptr);
            inputBuffers.emplace_back(std::move(buffer));
        }

        for (size_t i = 0; i < numBuffers; i++) {
            paramsBuffers[i] = Buffer::create(
                    context, paramsBufferSize,
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            VK_CHECK(paramsBuffers[i] != nullptr);

            stagingBuffers[i] = Buffer::create(
                    context, S2B(MAX_SAMPLE_ARRAY_SIZE),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            VK_CHECK(stagingBuffers[i] != nullptr);

            outputBuffers[i] = Buffer::create(
                    context, S2B(MAX_SAMPLE_ARRAY_SIZE >> tapBuffers.size()),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            VK_CHECK(outputBuffers[i] != nullptr);

            pParamsBuffers[i] = nullptr;
            pStagingBuffers[i] = nullptr;
            pOutputBuffers[i] = nullptr;

            shifters[i] = Pipelines::Shifter::create(context, groupSize, paramsBuffers[i].get(), stagingBuffers[i].get());
            VK_CHECK(shifters[i] != nullptr);

            for (size_t j = 0; j < taps.size() - 1; j++) {
                auto decimator = Pipelines::Decimator::create(context, groupSize, j, paramsBuffers[i].get(),
                                                              tapBuffers[j].get(), inputBuffers[j].get(), inputBuffers[j + 1].get());
                VK_CHECK(decimator != nullptr);
                decimators[i].emplace_back(std::move(decimator));
            }

            auto decimator = Pipelines::Decimator::create(context, groupSize, taps.size() - 1, paramsBuffers[i].get(),
                                                          tapBuffers[taps.size() - 1].get(), inputBuffers[taps.size() - 1].get(), outputBuffers[i].get());
            VK_CHECK(decimator != nullptr);
            decimators[i].emplace_back(std::move(decimator));

            fences[i] = std::make_unique<VulkanFence>(context->device());
            VK_CHECK(context->createFence(*fences[i]));

            for (size_t j = 0; j < 2; j++) {
                semaphores[i][j] = std::make_unique<VulkanSemaphore>(context->device());
                VK_CHECK(context->createSemaphore(*semaphores[i][j]));
            }
        }

        return true;
    }

    bool ShiftDecimatorMultiQueue::process(float *samples, size_t sampleCount, float phi, float omega) {
        const auto otherBufferIndex = (bufferIndex + 1) % numBuffers;

        counters.getTime(counters.totalTimestamp[0]);

        // Update parameters.
        if (pParamsBuffers[bufferIndex] == nullptr) {
            VK_CHECK(paramsBuffers[bufferIndex]->map((void **) &pParamsBuffers[bufferIndex], 0, paramsBufferSize));

            ((uint32_t *) pParamsBuffers[bufferIndex])[2] = 0;

            for (size_t i = 0; i < tapBuffers.size() - 1; i++) {
                ((uint32_t *) pParamsBuffers[bufferIndex])[3 + i] = tapBuffers[i + 1]->size() / sizeof(float) - 1;
            }

            ((uint32_t *) pParamsBuffers[bufferIndex])[3 + tapBuffers.size() - 1] = 0;
        }

        ((float *) pParamsBuffers[bufferIndex])[0] = phi;
        ((float *) pParamsBuffers[bufferIndex])[1] = omega;

        // Copy samples to staging buffer.
        if (pStagingBuffers[bufferIndex] == nullptr) {
            VK_CHECK(stagingBuffers[bufferIndex]->map(&pStagingBuffers[bufferIndex], 0, S2B(sampleCount)));
        }

        memcpy(pStagingBuffers[bufferIndex], samples, S2B(sampleCount));

        // Stage 1 - shifter.
        if (commandBuffers[bufferIndex][0] == nullptr) {
            commandBuffers[bufferIndex][0] = std::make_unique<VulkanCommandBuffer>(context->device(), context->commandPool());
            auto *commandBuffer = commandBuffers[bufferIndex][0].get();
            // Begin command buffer.
            VK_CHECK(context->createCommandBuffer(*commandBuffer));
            VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
            // Reset timestamps.
            vkCmdResetQueryPool(*commandBuffer, context->queryPool(), 6 * bufferIndex, 6);
            // Get start timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 6 * bufferIndex + 0);
            // Run shifter.
            shifters[bufferIndex]->recordComputeCommands(*commandBuffer, sampleCount);
            // Wait for shifter to complete.
            Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
            // Get end timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 6 * bufferIndex + 1);
            // End command buffer
            VK_CALL(vkEndCommandBuffer, *commandBuffer);
        }

        // Stage 2 - first decimator.
        if (commandBuffers[bufferIndex][1] == nullptr) {
            commandBuffers[bufferIndex][1] = std::make_unique<VulkanCommandBuffer>(context->device(), context->commandPool());
            auto *commandBuffer = commandBuffers[bufferIndex][1].get();
            // Begin command buffer.
            VK_CHECK(context->createCommandBuffer(*commandBuffer));
            VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
            // Get start timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 6 * bufferIndex + 2);
            // Copy from staging buffer to input buffer.
            const VkBufferCopy stagingCopy = {.srcOffset = 0, .dstOffset = BOF(tapBuffers[0]), .size = S2B(sampleCount)};
            vkCmdCopyBuffer(*commandBuffer, stagingBuffers[bufferIndex]->handle(), inputBuffers[0]->handle(), 1, &stagingCopy);
            // Run decimator.
            decimators[bufferIndex][0]->recordComputeCommands(*commandBuffer, sampleCount >> 1);
            // Wait for decimator to complete.
            Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
            // Update input buffer.
            const VkBufferCopy bufferCopy = {.srcOffset = S2B(sampleCount), .dstOffset = 0, .size = BOF(tapBuffers[0])};
            vkCmdCopyBuffer(*commandBuffer, inputBuffers[0]->handle(), inputBuffers[0]->handle(), 1, &bufferCopy);
            // Get end timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 6 * bufferIndex + 3);
            // End command buffer
            VK_CALL(vkEndCommandBuffer, *commandBuffer);
        }

        // Stage 3 - remaining decimators.
        if (commandBuffers[bufferIndex][2] == nullptr) {
            commandBuffers[bufferIndex][2] = std::make_unique<VulkanCommandBuffer>(context->device(), context->commandPool());
            auto *commandBuffer = commandBuffers[bufferIndex][2].get();
            // Begin command buffer.
            VK_CHECK(context->createCommandBuffer(*commandBuffer));
            VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
            // Get start timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 6 * bufferIndex + 4);
            for (size_t i = 1; i < tapBuffers.size(); i++) {
                // Run decimator.
                decimators[bufferIndex][i]->recordComputeCommands(*commandBuffer, sampleCount >> (i + 1));
                // Wait for decimator to complete.
                Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
                // Update input buffer.
                const VkBufferCopy bufferUpdate = {.srcOffset = S2B(sampleCount >> i), .dstOffset = 0, .size = BOF(tapBuffers[i])};
                vkCmdCopyBuffer(*commandBuffer, inputBuffers[i]->handle(), inputBuffers[i]->handle(), 1, &bufferUpdate);
            }
            // Get end timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 6 * bufferIndex + 5);
            // End command buffer
            VK_CALL(vkEndCommandBuffer, *commandBuffer);
        }

        // Reset fence.
        VK_CALL(vkResetFences, context->device(), 1, *fences[bufferIndex]);
        // Submit command buffers.
        VK_CHECK(Submit());

        // Wait for other queue to complete.
        counters.getTime(counters.waitTimestamp[0]);
        VK_CALL(vkWaitForFences, context->device(), 1, *fences[otherBufferIndex], true, -1ull);
        counters.getTime(counters.waitTimestamp[1]);

        if (pOutputBuffers[otherBufferIndex] == nullptr) {
            VK_CHECK(outputBuffers[otherBufferIndex]->map(&pOutputBuffers[otherBufferIndex], 0, S2B(sampleCount >> tapBuffers.size())));
        }

        // Copy samples from output buffer.
        memcpy(samples, pOutputBuffers[otherBufferIndex], S2B(sampleCount >> tapBuffers.size()));

        // Swap buffers.
        bufferIndex = otherBufferIndex;

        if (firstSubmit) {
            firstSubmit = false;
        }

        counters.getTime(counters.totalTimestamp[1]);

        // Update timestamp stats.
        if (counters.enable) {
            updateCounters();
        }

        return true;
    }

    bool ShiftDecimatorMultiQueue::Submit() {
        const auto otherBufferIndex = (bufferIndex + 1) % numBuffers;
        static const VkFlags stageFlags = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;

        const std::vector<VkSubmitInfo> submitInfo = {
                {
                        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                        .pNext = nullptr,
                        .waitSemaphoreCount = 0,
                        .pWaitSemaphores = nullptr,
                        .pWaitDstStageMask = &stageFlags,
                        .commandBufferCount = 1,
                        .pCommandBuffers = *commandBuffers[bufferIndex][0],
                        .signalSemaphoreCount = 0,
                        .pSignalSemaphores = nullptr,
                },
                {
                        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                        .pNext = nullptr,
                        .waitSemaphoreCount = firstSubmit ? 0 : 1u,
                        .pWaitSemaphores = (VkSemaphore *) *semaphores[otherBufferIndex][0],
                        .pWaitDstStageMask = &stageFlags,
                        .commandBufferCount = 1,
                        .pCommandBuffers = *commandBuffers[bufferIndex][1],
                        .signalSemaphoreCount = 1,
                        .pSignalSemaphores = (VkSemaphore *) *semaphores[bufferIndex][0],
                },
                {
                        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                        .pNext = nullptr,
                        .waitSemaphoreCount = firstSubmit ? 0 : 1u,
                        .pWaitSemaphores = (VkSemaphore *) *semaphores[otherBufferIndex][1],
                        .pWaitDstStageMask = &stageFlags,
                        .commandBufferCount = 1,
                        .pCommandBuffers = *commandBuffers[bufferIndex][2],
                        .signalSemaphoreCount = 1,
                        .pSignalSemaphores = (VkSemaphore *) *semaphores[bufferIndex][1],
                }
        };
        VK_CALL(vkQueueSubmit, context->queue(bufferIndex), submitInfo.size(), submitInfo.data(), *fences[bufferIndex]);

        return true;
    }

    void ShiftDecimatorMultiQueue::updateCounters() {
        auto delta = (counters.waitTimestamp[1].tv_sec - counters.waitTimestamp[0].tv_sec) * 1000000L +
                     (counters.waitTimestamp[1].tv_nsec - counters.waitTimestamp[0].tv_nsec) / 1000L;
        counters.fenceWaitTimeMin = std::min(counters.fenceWaitTimeMin, (unsigned long) delta);
        counters.fenceWaitTimeMax = std::max(counters.fenceWaitTimeMax, (unsigned long) delta);
        counters.fenceWaitTimeSum = counters.fenceWaitTimeSum + delta;

        delta = (counters.totalTimestamp[1].tv_sec - counters.totalTimestamp[0].tv_sec) * 1000000L +
                (counters.totalTimestamp[1].tv_nsec - counters.totalTimestamp[0].tv_nsec) / 1000L;
        counters.totalTimeMin = std::min(counters.totalTimeMin, (unsigned long) delta);
        counters.totalTimeMax = std::max(counters.totalTimeMax, (unsigned long) delta);
        counters.totalTimeSum = counters.totalTimeSum + delta;

        uint64_t values[12];
        vkGetQueryPoolResults(context->device(), context->queryPool(),
                              6 * bufferIndex, 6, sizeof(values), values, 2 * sizeof(uint64_t),
                              VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);

        for (size_t j = 0; j < 3; j++) {
            if (values[4 * j + 1] != 0 && values[4 * j + 3] != 0) {
                const auto t0 = values[4 * j + 0];
                const auto t1 = values[4 * j + 2];
                const auto commandTime = float(t1 - t0) * context->timestampPeriod() / 1000.0f;
                counters.stages[j].timeMin = std::min(counters.stages[j].timeMin, commandTime);
                counters.stages[j].timeMax = std::max(counters.stages[j].timeMax, commandTime);
                counters.stages[j].timeSum = counters.stages[j].timeSum + commandTime;
            }
        }

        if (++counters.counter == 120) {
            LOGD("DSP "
                 "stage 1: %4.0fus / %4.0fus / %4.0fus, "
                 "stage 2: %4.0fus / %4.0fus / %4.0fus, "
                 "stage 3: %4.0fus / %4.0fus / %4.0fus, "
                 "wait: %4luus / %4luus / %4luus, "
                 "total: %4luus / %4luus / %4luus",
                 counters.stages[0].timeMin, counters.stages[0].timeSum / counters.counter, counters.stages[0].timeMax,
                 counters.stages[1].timeMin, counters.stages[1].timeSum / counters.counter, counters.stages[1].timeMax,
                 counters.stages[2].timeMin, counters.stages[2].timeSum / counters.counter, counters.stages[2].timeMax,
                 counters.fenceWaitTimeMin, counters.fenceWaitTimeSum / counters.counter, counters.fenceWaitTimeMax,
                 counters.totalTimeMin, counters.totalTimeSum / counters.counter, counters.totalTimeMax);

            for (auto &stage: counters.stages) {
                stage.timeMin = MAXFLOAT;
                stage.timeMax = 0.0f;
                stage.timeSum = 0.0f;
            }

            counters.totalTimeMin = counters.fenceWaitTimeMin = LONG_MAX;
            counters.totalTimeMax = counters.fenceWaitTimeMax = 0;
            counters.totalTimeSum = counters.fenceWaitTimeSum = 0;

            counters.counter = 0;
        }
    }

    ShiftDecimatorMultiQueue::~ShiftDecimatorMultiQueue() {
        context->queueWaitIdle(0);
        context->queueWaitIdle(1);
    }
}
