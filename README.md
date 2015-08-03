# j6502

This is one of my forever incomplete pet projects (but very dear to my heart since the C64 was my first computer and the one I learned programming on ;-)

Since the emulator is constantly under development, I added quite a lot of tooling around it to ease in debugging stuff.

This screenshot should explain it :)

<img src="https://github.com/toby1984/j6502/blob/master/screenshot.png?raw=true" width="640" height="480" />

What's working:

- 6502 assembler (supports local labels and expressions but no macros and currently no illegal opcodes)
- 6502 disassembler (does not support illegal opcodes) 
- CPU (most illegal opcodes are currently missing, preventing games like Blue max etc. from running)
- CIA 
- VIC with sprites (regular & multi-color), raster IRQ and all valid text/graphics mode (regular/multi-color/extended-bg-color)
- IEC bus-level emulation 
- D64 file support
- loading programs/directory from D64 file

What's not working:

- VIC implementation is very crude and doesn't respect the actual cycle timings (apart from the fact that each raster line takes 63 cycles to render => PAL VIC)
- VIC currently does not trigger IRQ on sprite-sprite/sprite-background collisions
- Writing/modifying D64 files is not implemented (and thus SAVE cannot work even if my floppy emulation had it implemented)
- no SID
- floppy only supports LOAD and nothing else (and even this will most likely not work for REL files etc. because my filesystem implementation is not dealing with those yet)
