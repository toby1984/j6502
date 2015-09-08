# j6502

This is one of my forever incomplete pet projects (but very dear to my heart since the C64 was my first computer and the one I learned programming on ;-)

Since the emulator is constantly under development, I added quite a lot of tooling around it to ease in debugging stuff.

This screenshot should explain it :)

<img src="https://github.com/toby1984/j6502/blob/master/screenshot.png?raw=true" width="640" height="480" />

What's working:

- 6502 assembler (supports local labels and expressions but no macros and currently only a few illegal opcodes)
- 6502 disassembler (supports all illegal opcodes) 
- CPU including support for illegal opcodes
- supports .d64 and .g64 files
- true 1541 drive emulation
- CIA 6526 
- VIA 6522
- VIC with sprites (regular & multi-color), raster IRQ and all valid text/graphics mode (regular/multi-color/extended-bg-color)

What's not working:

- emulation currently chokes on most "sophisticated" code (demos, games later than 1984 ), needs debugging...
- VIC implementation is very crude and doesn't respect the actual cycle timings (apart from the fact that each raster line takes 63 cycles to render => PAL VIC)
- VIC currently does not trigger IRQ on sprite-sprite/sprite-background collisions
- Writing to disks is currently not implemented
- no SID
