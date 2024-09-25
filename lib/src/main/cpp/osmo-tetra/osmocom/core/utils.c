#include "utils.h"

#include <stdint.h>

const char *get_value_string_or_null(const struct value_string *vs,
                                     uint32_t val)
{
    int i;

    if (!vs)
        return NULL;

    for (i = 0;; i++) {
        if (vs[i].value == 0 && vs[i].str == NULL)
            break;
        if (vs[i].value == val)
            return vs[i].str;
    }

    return NULL;
}

const char *get_value_string(const struct value_string *vs, uint32_t val)
{
    const char *str = get_value_string_or_null(vs, val);
    if (str)
        return str;

    static char namebuf[255];
    snprintf(namebuf, sizeof(namebuf), "unknown 0x%"PRIx32, val);
    namebuf[sizeof(namebuf) - 1] = '\0';
    return namebuf;
}