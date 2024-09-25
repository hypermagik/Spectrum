#pragma once

/*! primitive operation */
enum osmo_prim_operation {
    PRIM_OP_REQUEST,	/*!< request */
    PRIM_OP_RESPONSE,	/*!< response */
    PRIM_OP_INDICATION,	/*!< indication */
    PRIM_OP_CONFIRM,	/*!< confirm */
};

/*! Osmocom primitive header */
struct osmo_prim_hdr {
    unsigned int sap;	/*!< Service Access Point Identifier */
    unsigned int primitive;	/*!< Primitive number */
    enum osmo_prim_operation operation; /*! Primitive Operation */
    struct msgb *msg;	/*!< \ref msgb containing associated data.
       * Note this can be slightly confusing, as the \ref osmo_prim_hdr
       * is stored inside a \ref msgb, but then it contains a pointer
       * back to the msgb.  This is to simplify development: You can
       * pass around a \ref osmo_prim_hdr by itself, and any function
       * can autonomously resolve the underlying msgb, if needed (e.g.
       * for \ref msgb_free. */
};
