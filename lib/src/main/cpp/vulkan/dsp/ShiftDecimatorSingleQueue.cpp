#include "ShiftDecimatorSingleQueue.h"
#include "vulkan/Utils.h"

namespace Vulkan::DSP {
    std::unique_ptr<ShiftDecimatorSingleQueue> ShiftDecimatorSingleQueue::create(Context *context, Taps &&taps) {
        if (context == nullptr) {
            return nullptr;
        }
        auto processor = std::make_unique<ShiftDecimatorSingleQueue>(context);
        const bool success = processor->initialize(taps);
        return success ? std::move(processor) : nullptr;
    }

    bool ShiftDecimatorSingleQueue::initialize(Taps &taps) {
        for (size_t i = 0; i < taps.size(); i++) {
            auto buffer = Buffer::create(
                    context, F2B(taps[i].size()),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            VK_CHECK(buffer != nullptr);
            buffer->copyFrom(taps[i].data(), 0, F2B(taps[i].size()));
            tapBuffers.emplace_back(std::move(buffer));

            buffer = Buffer::create(
                    context, S2B(taps[i].size() - 1 + (MAX_SAMPLE_ARRAY_SIZE >> i)),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            VK_CHECK(buffer != nullptr);
            inputBuffers.emplace_back(std::move(buffer));
        }

        paramsBuffer = Buffer::create(
                context, paramsBufferSize,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VK_CHECK(paramsBuffer != nullptr);

        outputBuffer = Buffer::create(
                context, S2B(MAX_SAMPLE_ARRAY_SIZE >> tapBuffers.size()),
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VK_CHECK(outputBuffer != nullptr);

        shifter = Pipelines::Shifter::create(context, groupSize, paramsBuffer.get(), inputBuffers[0].get());
        VK_CHECK(shifter != nullptr);

        for (size_t i = 0; i < taps.size() - 1; i++) {
            auto decimator = Pipelines::Decimator::create(context, groupSize, i, paramsBuffer.get(),
                                                          tapBuffers[i].get(), inputBuffers[i].get(), inputBuffers[i + 1].get());
            VK_CHECK(decimator != nullptr);
            decimators.emplace_back(std::move(decimator));
        }

        auto decimator = Pipelines::Decimator::create(context, groupSize, taps.size() - 1, paramsBuffer.get(),
                                                      tapBuffers[taps.size() - 1].get(), inputBuffers[taps.size() - 1].get(), outputBuffer.get());
        VK_CHECK(decimator != nullptr);
        decimators.emplace_back(std::move(decimator));

        for (size_t i = 0; i < numBuffers; i++) {
            for (size_t j = 0; j < numStages; j++) {
                fences[i][j] = std::make_unique<VulkanFence>(context->device());
                VK_CHECK(context->createFence(*fences[i][j]));
            }
        }

        return true;
    }

    bool ShiftDecimatorSingleQueue::process(float *samples, size_t sampleCount, float phi, float omega) {
        const auto otherBufferIndex = (bufferIndex + 1) % numBuffers;

        counters.getTime(counters.totalTimestamp[0]);

        // Wait for other buffer to complete stage 1.
        counters.getTime(counters.stages[0].fenceWaitTimestamp[0]);
        VK_CALL(vkWaitForFences, context->device(), 1, *fences[otherBufferIndex][0], true, -1ull);
        counters.getTime(counters.stages[0].fenceWaitTimestamp[1]);

        // Update parameters.
        if (pParamsBuffer == nullptr) {
            VK_CHECK(paramsBuffer->map((void **) &pParamsBuffer, 0, paramsBufferSize));

            ((uint32_t *) pParamsBuffer)[2] = tapBuffers[0]->size() / sizeof(float) - 1;

            for (size_t i = 0; i < tapBuffers.size() - 1; i++) {
                ((uint32_t *) pParamsBuffer)[3 + i] = tapBuffers[i + 1]->size() / sizeof(float) - 1;
            }

            ((uint32_t *) pParamsBuffer)[3 + tapBuffers.size() - 1] = 0;
        }

        ((float *) pParamsBuffer)[0] = phi;
        ((float *) pParamsBuffer)[1] = omega;

        // Copy samples to input buffer.
        if (pInputBuffer == nullptr) {
            VK_CHECK(inputBuffers[0]->map(&pInputBuffer, BOF(tapBuffers[0]), S2B(sampleCount)));
        }

        memcpy(pInputBuffer, samples, S2B(sampleCount));

        // Stage 1 - shifter and first decimator.
        if (commandBuffers[bufferIndex][0] == nullptr) {
            commandBuffers[bufferIndex][0] = std::make_unique<VulkanCommandBuffer>(context->device(), context->commandPool());
            auto *commandBuffer = commandBuffers[bufferIndex][0].get();
            // Begin command buffer.
            VK_CHECK(context->createCommandBuffer(*commandBuffer));
            VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
            // Reset timestamps.
            vkCmdResetQueryPool(*commandBuffer, context->queryPool(), 4 * bufferIndex, 4);
            // Get start timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, context->queryPool(), 4 * bufferIndex + 0);
            // Run shifter.
            shifter->recordComputeCommands(*commandBuffer, sampleCount);
            // Wait for shifter to complete.
            Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
            // Run decimator.
            decimators[0]->recordComputeCommands(*commandBuffer, sampleCount >> 1);
            // Wait for decimator to complete.
            Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
            // Update input buffer.
            const VkBufferCopy bufferUpdate0 = {.srcOffset = S2B(sampleCount), .dstOffset = 0, .size = BOF(tapBuffers[0])};
            vkCmdCopyBuffer(*commandBuffer, inputBuffers[0]->handle(), inputBuffers[0]->handle(), 1, &bufferUpdate0);
            // Get end timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 4 * bufferIndex + 1);
            // End command buffer
            VK_CALL(vkEndCommandBuffer, *commandBuffer);
        }
        // Reset fence and submit stage 1.
        VK_CALL(vkResetFences, context->device(), 1, *fences[bufferIndex][0]);
        VK_CHECK(context->submitCommandBuffer(*commandBuffers[bufferIndex][0], *fences[bufferIndex][0], 0));

        // Wait for other buffer to complete stage 2.
        counters.getTime(counters.stages[1].fenceWaitTimestamp[0]);
        VK_CALL(vkWaitForFences, context->device(), 1, *fences[otherBufferIndex][1], true, -1ull);
        counters.getTime(counters.stages[1].fenceWaitTimestamp[1]);

        if (pOutputBuffer == nullptr) {
            VK_CHECK(outputBuffer->map(&pOutputBuffer, 0, S2B(sampleCount >> tapBuffers.size())));
        }

        // Copy samples from output buffer.
        memcpy(samples, pOutputBuffer, S2B(sampleCount >> tapBuffers.size()));

        // Start recording stage 2.
        if (commandBuffers[bufferIndex][1] == nullptr) {
            commandBuffers[bufferIndex][1] = std::make_unique<VulkanCommandBuffer>(context->device(), context->commandPool());
            auto *commandBuffer = commandBuffers[bufferIndex][1].get();
            // Begin command buffer.
            VK_CHECK(context->createCommandBuffer(*commandBuffer));
            VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
            // Get start timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 4 * bufferIndex + 2);
            for (size_t i = 1; i < tapBuffers.size(); i++) {
                // Run decimator.
                decimators[i]->recordComputeCommands(*commandBuffer, sampleCount >> (i + 1));
                // Wait for decimator to complete.
                Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
                // Update input buffer.
                const VkBufferCopy bufferUpdate = {.srcOffset = S2B(sampleCount >> i), .dstOffset = 0, .size = BOF(tapBuffers[i])};
                vkCmdCopyBuffer(*commandBuffer, inputBuffers[i]->handle(), inputBuffers[i]->handle(), 1, &bufferUpdate);
            }
            // Get end timestamp.
            vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 4 * counters.counter + 3);
            // End command buffer
            VK_CALL(vkEndCommandBuffer, *commandBuffer);
        }
        // Reset fence and submit stage 2.
        VK_CALL(vkResetFences, context->device(), 1, *fences[bufferIndex][1]);
        VK_CHECK(context->submitCommandBuffer(*commandBuffers[bufferIndex][1], *fences[bufferIndex][1], 0));

        // Swap buffers.
        bufferIndex = otherBufferIndex;

        counters.getTime(counters.totalTimestamp[1]);

        // Update timestamp stats.
        if (counters.enable) {
            updateCounters();
        }

        return true;
    }

    void ShiftDecimatorSingleQueue::updateCounters() {
        for (auto &stage: counters.stages) {
            auto delta = (stage.fenceWaitTimestamp[1].tv_sec - stage.fenceWaitTimestamp[0].tv_sec) * 1000000L +
                         (stage.fenceWaitTimestamp[1].tv_nsec - stage.fenceWaitTimestamp[0].tv_nsec) / 1000L;
            stage.fenceWaitTimeMin = std::min(stage.fenceWaitTimeMin, (unsigned long) delta);
            stage.fenceWaitTimeMax = std::max(stage.fenceWaitTimeMax, (unsigned long) delta);
            stage.fenceWaitTimeSum = stage.fenceWaitTimeSum + delta;
        }

        auto delta = (counters.totalTimestamp[1].tv_sec - counters.totalTimestamp[0].tv_sec) * 1000000L +
                     (counters.totalTimestamp[1].tv_nsec - counters.totalTimestamp[0].tv_nsec) / 1000L;
        counters.totalTimeMin = std::min(counters.totalTimeMin, (unsigned long) delta);
        counters.totalTimeMax = std::max(counters.totalTimeMax, (unsigned long) delta);
        counters.totalTimeSum = counters.totalTimeSum + delta;

        uint64_t values[8];
        vkGetQueryPoolResults(context->device(), context->queryPool(),
                              4 * bufferIndex, 4, sizeof(values), values, 2 * sizeof(uint64_t),
                              VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);

        for (size_t j = 0; j < numStages; j++) {
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
                 "stage 1 wait: %4luus / %4luus / %4luus, "
                 "stage 2 wait: %4luus / %4luus / %4luus, "
                 "total: %4luus / %4luus / %4luus",
                 counters.stages[0].timeMin, counters.stages[0].timeSum / counters.counter, counters.stages[0].timeMax,
                 counters.stages[1].timeMin, counters.stages[1].timeSum / counters.counter, counters.stages[1].timeMax,
                 counters.stages[0].fenceWaitTimeMin, counters.stages[0].fenceWaitTimeSum / counters.counter, counters.stages[0].fenceWaitTimeMax,
                 counters.stages[1].fenceWaitTimeMin, counters.stages[1].fenceWaitTimeSum / counters.counter, counters.stages[1].fenceWaitTimeMax,
                 counters.totalTimeMin, counters.totalTimeSum / counters.counter, counters.totalTimeMax);

            for (auto &stage: counters.stages) {
                stage.timeMin = MAXFLOAT;
                stage.timeMax = 0.0f;
                stage.timeSum = 0.0f;
                stage.fenceWaitTimeMin = LONG_MAX;
                stage.fenceWaitTimeMax = 0;
                stage.fenceWaitTimeSum = 0;
            }

            counters.totalTimeMin = LONG_MAX;
            counters.totalTimeMax = 0;
            counters.totalTimeSum = 0;

            counters.counter = 0;
        }
    }

    ShiftDecimatorSingleQueue::~ShiftDecimatorSingleQueue() {
        context->queueWaitIdle(0);
    }
}
