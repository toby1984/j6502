# j6502

This is one of my forever incomplete pet projects (but very dear to my heart since the C64 was my first computer and the one I learned programming on ;-)

Since the emulator is constantly under development, I added quite a lot of tooling around it to ease in debugging stuff.

This screenshot should explain it :)



What's working:

- CPU
- CIA (partially, some things are most likely broken)
- IEC bus-level emulation 
- loading programs/directory from D64 file
- D64 file support

What's not working:

- Writing/modifying D64 files is not implemented (and thus SAVE cannot work even if my floppy emulation had it implemented)
- no SID
- (almost) no VIC , just a crude hack to display textmode
- floppy only supports LOAD and nothing else (and even this will most likely not work for REL files etc.)
