#include "utils.h"
#include <stddef.h>
#include <string.h>

static __thread char hexd_buff[4096];

/*! Convert a sequence of unpacked bits to ASCII string, in user-supplied buffer.
 * \param[out] buf caller-provided output string buffer
 * \param[out] buf_len size of buf in bytes
 * \param[in] bits A sequence of unpacked bits
 * \param[in] len Length of bits
 * \return The output buffer (buf).
 */
static char *osmo_ubit_dump_buf(char *buf, size_t buf_len, const uint8_t *bits, unsigned int len)
{
    unsigned int i;

    if (len > buf_len-1)
        len = buf_len-1;
    memset(buf, 0, buf_len);

    for (i = 0; i < len; i++) {
        char outch;
        switch (bits[i]) {
            case 0:
                outch = '0';
                break;
            case 0xff:
                outch = '?';
                break;
            case 1:
                outch = '1';
                break;
            default:
                outch = 'E';
                break;
        }
        buf[i] = outch;
    }
    buf[buf_len-1] = 0;
    return buf;
}

/*! Convert a sequence of unpacked bits to ASCII string, in static buffer.
 * \param[in] bits A sequence of unpacked bits
 * \param[in] len Length of bits
 * \returns string representation in static buffer.
 */
char *osmo_ubit_dump(const uint8_t *bits, unsigned int len)
{
    return osmo_ubit_dump_buf(hexd_buff, sizeof(hexd_buff), bits, len);
}
