// nv:: (the GPU driver glue layer beneath NVN: creates transfer memory, connects to the nvdrv service). NVN has been replaced wholesale with a host null device, so this layer has nothing to do.
// The allocator callbacks are recorded in the host global g_nv_alloc; the graphics layer takes them from here later when it needs to allocate memory with the game's allocator.
#include "../host/hle.h"
#include <stdatomic.h>

typedef void *(*nv_alloc_fn)(unsigned long size, unsigned long align, void *user);
typedef void (*nv_free_fn)(void *p, void *user);
typedef void *(*nv_realloc_fn)(void *p, unsigned long size, void *user);
struct nv_allocator { nv_alloc_fn alloc; nv_free_fn free; nv_realloc_fn realloc; void *user; };
struct nv_allocator g_nv_alloc, g_nv_devtools_alloc;

#define WHY "NVN has been replaced wholesale with a host null device, so the driver glue layer has nothing to do"
HLE_FORCE("_ZN2nv20SetGraphicsAllocatorEPFPvmmS0_EPFvS0_S0_EPFS0_S0_mS0_ES0_", WHY "; allocator recorded in g_nv_alloc",
          void, h_nv_SetAlloc, nv_alloc_fn a, nv_free_fn f, nv_realloc_fn r, void *u) { g_nv_alloc = (struct nv_allocator){a, f, r, u}; }
HLE_FORCE("_ZN2nv28SetGraphicsDevtoolsAllocatorEPFPvmmS0_EPFvS0_S0_EPFS0_S0_mS0_ES0_", WHY "; allocator recorded in g_nv_devtools_alloc",
          void, h_nv_SetDevAlloc, nv_alloc_fn a, nv_free_fn f, nv_realloc_fn r, void *u) { g_nv_devtools_alloc = (struct nv_allocator){a, f, r, u}; }
// The pool the game hands over here is the whole of what the NV side could allocate from; nothing in the host allocates from it (NVN is a null device), so all of it stays free and the queries below report
// exactly that. Kept per process: a game that re-initialises the driver replaces the size
static _Atomic uint64_t g_nv_size;
HLE_FORCE("_ZN2nv18InitializeGraphicsEPvm", WHY "; the pool's size is kept for GetGraphicsMemoryInfo", void, h_nv_Init, void *mem, unsigned long size) { (void)mem; atomic_store(&g_nv_size, (uint64_t)size); }
HLE_FORCE("_ZN2nv16FinalizeGraphicsEv", WHY, void, h_nv_Fin, void) {}
// nv::MemoryInfo = { uint64 currentFreeSize, uint64 currentLargestAllocationSize, uint64 worstCaseFreeSize, uint64 worstCaseLargestAllocationSize }: the SDK's DumpGraphicsMemoryInfo prints the four, in order,
// as "NV Current Free", "NV Current Largest Allocation Available", "NV Worst-Case Free Size" and "NV Worst-Case Largest Allocation Available" (Death's Gambit: Afterlife logs all four at boot, main+0x80fd8a4)
// and calls the SDK's DumpGraphicsMemoryInfo right before them (main+0x80fd8a0), which dereferences the nvos driver object no host ever creates: the null read at sdk+0x422fb8 that ended the process
typedef struct { uint64_t current_free, current_largest, worst_free, worst_largest; } nv_meminfo;
HLE_FORCE("_ZN2nv21GetGraphicsMemoryInfoEPNS_10MemoryInfoE", WHY "; the driver's whole pool reads as free", void, h_nv_MemInfo, nv_meminfo *i) {
    uint64_t sz = atomic_load(&g_nv_size);
    if (i) *i = (nv_meminfo){sz, sz, sz, sz};
}
HLE_FORCE("_ZN2nv22DumpGraphicsMemoryInfoEv", WHY "; the SDK's version needs the driver object, the host prints nothing", void, h_nv_Dump, void) {}
