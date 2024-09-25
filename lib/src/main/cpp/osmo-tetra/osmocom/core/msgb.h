#pragma once

#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

/*! Osmocom message buffer */
struct msgb {
    // struct llist_head list; /*!< linked list header */


    /* Part of which TRX logical channel we were received / transmitted */
    /* FIXME: move them into the control buffer */
    union {
        void *dst; /*!< reference of origin/destination */
        struct gsm_bts_trx *trx;
    };
    struct gsm_lchan *lchan; /*!< logical channel */

    unsigned char *l1h; /*!< pointer to Layer1 header (if any) */
    unsigned char *l2h; /*!< pointer to A-bis layer 2 header: OML, RSL(RLL), NS */
    unsigned char *l3h; /*!< pointer to Layer 3 header. For OML: FOM; RSL: 04.08; GPRS: BSSGP */
    unsigned char *l4h; /*!< pointer to layer 4 header */

    unsigned long cb[5]; /*!< control buffer */

    uint16_t data_len;   /*!< length of underlying data array */
    uint16_t len;	     /*!< length of bytes used in msgb */

    unsigned char *head;	/*!< start of underlying memory buffer */
    unsigned char *tail;	/*!< end of message in buffer */
    unsigned char *data;	/*!< start of message in buffer */
    unsigned char _data[0]; /*!< optional immediate data array */
};

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
struct msgb *msgb_alloc_c(uint16_t size, const char *name);

/*! Allocate a new message buffer from tall_msgb_ctx
 * \param[in] size Length in octets, including headroom
 * \param[in] name Human-readable name to be associated with msgb
 * \returns dynamically-allocated \ref msgb
 *
 * This function allocates a 'struct msgb' as well as the underlying
 * memory buffer for the actual message data (size specified by \a size)
 * using the talloc memory context previously set by \ref msgb_set_talloc_ctx
 */
struct msgb *msgb_alloc(uint16_t size, const char *name);

/*! obtain L1 header of msgb */
#define msgb_l1(m)	((void *)((m)->l1h))
/*! obtain L2 header of msgb */
#define msgb_l2(m)	((void *)((m)->l2h))
/*! obtain L3 header of msgb */
#define msgb_l3(m)	((void *)((m)->l3h))
/*! obtain L4 header of msgb */
#define msgb_l4(m)	((void *)((m)->l4h))
/*! obtain SMS header of msgb */
#define msgb_sms(m)	msgb_l4(m)


/*! determine length of L1 message
 *  \param[in] msgb message buffer
 *  \returns size of L1 message in bytes
 *
 * This function computes the number of bytes between the tail of the
 * message and the layer 1 header.
 */
static inline unsigned int msgb_l1len(const struct msgb *msgb)
{
    assert(msgb->l1h);
    return msgb->tail - (uint8_t *)msgb_l1(msgb);
}

/*! determine length of L2 message
 *  \param[in] msgb message buffer
 *  \returns size of L2 message in bytes
 *
 * This function computes the number of bytes between the tail of the
 * message and the layer 2 header.
 */
static inline unsigned int msgb_l2len(const struct msgb *msgb)
{
    assert(msgb->l2h);
    return msgb->tail - (uint8_t *)msgb_l2(msgb);
}

/*! determine length of L3 message
 *  \param[in] msgb message buffer
 *  \returns size of L3 message in bytes
 *
 * This function computes the number of bytes between the tail of the
 * message and the layer 3 header.
 */
static inline unsigned int msgb_l3len(const struct msgb *msgb)
{
    assert(msgb->l3h);
    return msgb->tail - (uint8_t *)msgb_l3(msgb);
}

/*! determine length of L4 message
 *  \param[in] msgb message buffer
 *  \returns size of L4 message in bytes
 *
 * This function computes the number of bytes between the tail of the
 * message and the layer 4 header.
 */
static inline unsigned int msgb_l4len(const struct msgb *msgb)
{
    assert(msgb->l4h);
    return msgb->tail - (uint8_t *)msgb_l4(msgb);
}

/*! determine the length of the header
 *  \param[in] msgb message buffer
 *  \returns number of bytes between start of buffer and start of msg
 *
 * This function computes the length difference between the underlying
 * data buffer and the used section of the \a msgb.
 */
static inline unsigned int msgb_headlen(const struct msgb *msgb)
{
    return msgb->len - msgb->data_len;
}

static inline int msgb_tailroom(const struct msgb *msgb)
{
    return (msgb->head + msgb->data_len) - msgb->tail;
}

/*! append data to end of message buffer
 *  \param[in] msgb message buffer
 *  \param[in] len number of bytes to append to message
 *  \returns pointer to start of newly-appended data
 *
 * This function will move the \a tail pointer of the message buffer \a
 * len bytes further, thus enlarging the message by \a len bytes.
 *
 * The return value is a pointer to start of the newly added section at
 * the end of the message and can be used for actually filling/copying
 * data into it.
 */
static inline unsigned char *msgb_put(struct msgb *msgb, unsigned int len)
{
    unsigned char *tmp = msgb->tail;
    if (msgb_tailroom(msgb) < (int) len) {
        printf("Not enough tailroom msgb_put\n");
        abort();
    }
    msgb->tail += len;
    msgb->len += len;
    return tmp;
}
