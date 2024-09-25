#pragma once

#include "msgb.h"
#include "talloc.h"
#include "bits.h"
#include <stdint.h>

#include <android/log.h>

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))
#endif

#define PRIx32 "x"

struct value_string {
    uint32_t value;		/*!< numeric value */
    const char *str;	/*!< human-readable string */
};

/*! get human-readable string or NULL for given value
 *  \param[in] vs Array of value_string tuples
 *  \param[in] val Value to be converted
 *  \returns pointer to human-readable string or NULL if val is not found
 */
const char *get_value_string(const struct value_string *vs, uint32_t val);

#define printf(...) // __android_log_print(ANDROID_LOG_DEBUG, "osmo-tetra", __VA_ARGS__)
#define fprintf(f, ...) // __android_log_print(ANDROID_LOG_DEBUG, "osmo-tetra", __VA_ARGS__)
