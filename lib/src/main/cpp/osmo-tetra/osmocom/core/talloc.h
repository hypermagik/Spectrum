#pragma once

#define talloc_zero(x, t) ({ void *_ptr = malloc(sizeof(t)); memset(_ptr, 0, sizeof(t)); _ptr; })
#define talloc_free(p) free(p)
#define talloc_zero_array(x, t, n) ({ void *_ptr = malloc(sizeof(t) * n); memset(_ptr, 0, sizeof(t) * n); _ptr; })
#define talloc_realloc(x, p, t, n) realloc(p, sizeof(t) * n)
