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
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
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

        outputBuffer = Buffer::create(
                context, S2B(MAX_SAMPLE_ARRAY_SIZE >> tapBuffers.size()),
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VK_CHECK(outputBuffer != nullptr);

        shifter = Pipelines::Shifter::create(context, groupSize, inputBuffers[0].get(), taps[0].size() - 1);
        VK_CHECK(shifter != nullptr);

        for (size_t i = 0; i < taps.size() - 1; i++) {
            auto decimator = Pipelines::Decimator::create(context, groupSize, tapBuffers[i].get(), inputBuffers[i].get());
            VK_CHECK(decimator != nullptr);
            decimator->setOutput(inputBuffers[i + 1].get(), taps[i + 1].size() - 1);
            decimators.emplace_back(std::move(decimator));
        }

        auto decimator = Pipelines::Decimator::create(context, groupSize, tapBuffers[taps.size() - 1].get(), inputBuffers[taps.size() - 1].get());
        VK_CHECK(decimator != nullptr);
        decimator->setOutput(outputBuffer.get(), 0);
        decimators.emplace_back(std::move(decimator));

        for (size_t i = 0; i < numBuffers; i++) {
            for (size_t j = 0; j < numStages; j++) {
                fences[i][j] = std::make_unique<VulkanFence>(context->device());
                VK_CHECK(context->createFence(*fences[i][j]));

                commandBuffers[i][j] = std::make_unique<VulkanCommandBuffer>(context->device(), context->commandPool());
                VK_CHECK(context->createCommandBuffer(*commandBuffers[i][j]));
            }
        }

        return true;
    }

    bool ShiftDecimatorSingleQueue::process(float *samples, size_t sampleCount, float phi, float omega) {
        const auto otherBufferIndex = (bufferIndex + 1) % numBuffers;

        counters.getTime(counters.totalTimestamp[0]);

        if (pInputBuffer == nullptr) {
            VK_CHECK(inputBuffers[0]->map(&pInputBuffer, BOF(tapBuffers[0]), S2B(sampleCount)));
        }

        // Wait for other buffer to complete stage 1.
        counters.getTime(counters.stages[0].fenceWaitTimestamp[0]);
        VK_CALL(vkWaitForFences, context->device(), 1, *fences[otherBufferIndex][0], true, -1ull);
        counters.getTime(counters.stages[0].fenceWaitTimestamp[1]);

        // Copy samples to input buffer.
        memcpy(pInputBuffer, samples, S2B(sampleCount));
        inputBuffers[0]->flush(BOF(tapBuffers[0]), S2B(sampleCount));

        // Start recording stage 1.
        auto stageIndex = 0;
        auto *commandBuffer = commandBuffers[bufferIndex][stageIndex].get();
        VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
        // Reset timestamps.
        vkCmdResetQueryPool(*commandBuffer, context->queryPool(), 2 * counters.counter, 2);
        // Get start timestamp.
        vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, context->queryPool(), 2 * counters.counter + 0);
        // Run shifter.
        shifter->recordComputeCommands(*commandBuffer, sampleCount, phi, omega);
        // Wait for shifter to complete.
        Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        // Run decimator.
        decimators[0]->recordComputeCommands(*commandBuffer, sampleCount >> 1);
        // Wait for decimator to complete.
        Context::addStageBarrier(*commandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        // Update input buffer.
        const VkBufferCopy bufferUpdate0 = {.srcOffset = S2B(sampleCount), .dstOffset = 0, .size = BOF(tapBuffers[0])};
        vkCmdCopyBuffer(*commandBuffer, inputBuffers[0]->handle(), inputBuffers[0]->handle(), 1, &bufferUpdate0);
        // Reset fence and submit stage 1.
        VK_CALL(vkResetFences, context->device(), 1, *fences[bufferIndex][stageIndex]);
        VK_CHECK(context->endAndSubmitCommandBuffer(*commandBuffer, nullptr, nullptr, *fences[bufferIndex][stageIndex], 0));

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
        stageIndex = 1;
        commandBuffer = commandBuffers[bufferIndex][stageIndex].get();
        VK_CHECK(Context::beginCommandBuffer(*commandBuffer));
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
        vkCmdWriteTimestamp(*commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, context->queryPool(), 2 * counters.counter + 1);
        // Reset fence and submit stage 2.
        VK_CALL(vkResetFences, context->device(), 1, *fences[bufferIndex][stageIndex]);
        VK_CHECK(context->endAndSubmitCommandBuffer(*commandBuffer, nullptr, nullptr, *fences[bufferIndex][stageIndex], 0));

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

        if (++counters.counter == 128) {
            uint64_t values[512];

            vkGetQueryPoolResults(context->device(), context->queryPool(),
                                  0, 256, sizeof(values), values, 2 * sizeof(uint64_t),
                                  VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);

            float commandTimeMin = MAXFLOAT;
            float commandTimeMax = 0.0f;
            float commandTimeSum = 0.0f;

            for (size_t i = 0; i < 128; i++) {
                if (values[4 * i + 1] != 0 && values[4 * i + 3] != 0) {
                    const auto commandTime = float(values[4 * i + 2] - values[4 * i + 0]) * context->timestampPeriod() / 1000.0f;
                    commandTimeMin = std::min(commandTimeMin, commandTime);
                    commandTimeMax = std::max(commandTimeMax, commandTime);
                    commandTimeSum = commandTimeSum + commandTime;
                }
            }

            LOGD("DSP GPU time: %4.0fus / %4.0fus / %4.0fus, "
                 "stage 0 wait: %4luus / %4luus / %4luus, "
                 "stage 1 wait: %4luus / %4luus / %4luus, "
                 "total: %4luus / %4luus / %4luus",
                 commandTimeMin, commandTimeSum / counters.counter, commandTimeMax,
                 counters.stages[0].fenceWaitTimeMin, counters.stages[0].fenceWaitTimeSum / counters.counter, counters.stages[0].fenceWaitTimeMax,
                 counters.stages[1].fenceWaitTimeMin, counters.stages[1].fenceWaitTimeSum / counters.counter, counters.stages[1].fenceWaitTimeMax,
                 counters.totalTimeMin, counters.totalTimeSum / counters.counter, counters.totalTimeMax);

            counters.totalTimeMin = counters.stages[0].fenceWaitTimeMin = counters.stages[1].fenceWaitTimeMin = LONG_MAX;
            counters.totalTimeMax = counters.stages[0].fenceWaitTimeMax = counters.stages[1].fenceWaitTimeMax = 0;
            counters.totalTimeSum = counters.stages[0].fenceWaitTimeSum = counters.stages[1].fenceWaitTimeSum = 0;

            counters.counter = 0;
        }
    }

    ShiftDecimatorSingleQueue::~ShiftDecimatorSingleQueue() {
        context->queueWaitIdle(0);
    }
}
