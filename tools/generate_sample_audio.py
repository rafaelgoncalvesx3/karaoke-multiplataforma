#!/usr/bin/env python3
"""Regenera amostras de audio demonstrativas (sem dependencias de terceiros)."""
from pathlib import Path
import wave, math, struct
ROOT=Path(__file__).resolve().parent.parent
RATE=22050
# Mesmo roteiro das partituras de amostra: 8 notas, GAP inicial 1500 ms.
NOTES=[(1.5,1,60),(2.5,1,62),(3.5,1,64),(4.5,1,65),
       (6.5,1,67),(7.5,1,69),(8.5,1,71),(9.5,2,72)]
DESTINATIONS=[
    ROOT/'01_Console_NET/Samples/escala.wav',
    ROOT/'02_Instalador_Windows/Samples/escala.wav',
    ROOT/'03_Android_TV/Samples/Exemplo/Album/escala.wav',
]
for destination in DESTINATIONS:
    destination.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(destination),'wb') as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(RATE)
        samples=bytearray()
        for i in range(RATE*12):
            t=i/RATE
            amplitude=0.
            for start,duration,midi in NOTES:
                if start <= t < start + duration:
                    frequency=440*2**((midi-69)/12)
                    envelope=max(0.,min(1.,(t-start)/.025,(start+duration-t)/.025))
                    amplitude=.18*envelope*math.sin(2*math.pi*frequency*(t-start))
                    break
            samples.extend(struct.pack('<h',int(amplitude*32767)))
        output.writeframes(samples)
    print('Gerado:',destination.relative_to(ROOT))
