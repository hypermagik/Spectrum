#include "msgb.h"
#include <string.h>

/*! Allocate a new message buffer from given talloc context
 * \param[in] ctx talloc context from which to allocate
 * \param[in] size Length in octets, including headroom
 * \param[in] name Human-readable name to be associated with msgb
 * \returns dynamically-allocated \ref msgb
 *
 * This function allocates a 'struct msgb' as well as the underlying
 * memory buffer for the actual message data (size specified by \a size)
 * using the talloc memory context previously set by \ref msgb_set_talloc_ctx
 */
struct msgb *msgb_alloc_c(uint16_t size, const char *name)
{
    struct msgb *msg;

    // msg = talloc_named_const(ctx, sizeof(*msg) + size, name);
    msg = (struct msgb*)malloc(sizeof(*msg) + size);
    if (!msg) {
        return NULL;
    }

    /* Manually zero-initialize allocated memory */
    memset(msg, 0x00, sizeof(*msg) + size);

    msg->data_len = size;
    msg->len = 0;
    msg->data = msg->_data;
    msg->head = msg->_data;
    msg->tail = msg->_data;

    return msg;
}

/*! Allocate a new message buffer from tall_msgb_ctx
 * \param[in] size Length in octets, including headroom
 * \param[in] name Human-readable name to be associated with msgb
 * \returns dynamically-allocated \ref msgb
 *
 * This function allocates a 'struct msgb' as well as the underlying
 * memory buffer for the actual message data (size specified by \a size)
 * using the talloc memory context previously set by \ref msgb_set_talloc_ctx
 */
struct msgb *msgb_alloc(uint16_t size, const char *name)
{
    return msgb_alloc_c(size, name);
}
