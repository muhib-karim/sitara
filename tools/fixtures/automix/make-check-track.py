import numpy as np, wave, sys
sr=22050; bpm=126.0; secs=34.0
n=int(sr*secs); x=np.zeros(n)
rng=np.random.default_rng(5)
beat=60/bpm; t=0.4; i=0
def add(start, sig):
    s=int(start*sr); e=min(n,s+len(sig)); x[s:e]+=sig[:e-s]
while t<secs-0.3:
    tt=np.arange(int(0.15*sr))/sr
    if i%4==0: add(t, 1.0*np.exp(-tt*30)*np.sin(2*np.pi*(50+80*np.exp(-tt*40))*tt)); add(t, 0.25*np.exp(-np.arange(int(beat*3.6*sr))/sr*2)*np.sin(2*np.pi*[55,65.4,49,73.4][(i//4)%4]*np.arange(int(beat*3.6*sr))/sr))
    if i%4==2: add(t, 0.6*np.exp(-tt*30)*np.sin(2*np.pi*(50+80*np.exp(-tt*40))*tt))
    if i%4 in (1,3): add(t, 0.5*np.exp(-tt[:int(0.12*sr)]*35)*rng.uniform(-1,1,int(0.12*sr)))
    h=np.diff(rng.uniform(-1,1,int(0.04*sr)+1))*np.exp(-np.arange(int(0.04*sr))/sr*120)
    add(t,0.15*h); add(t+beat/2,0.1*h)
    i+=1; t+=beat
x+=rng.uniform(-1,1,n)*0.01
x=x/np.max(np.abs(x))*0.8
w=wave.open(sys.argv[1],'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((x*32767).astype('<i2').tobytes()); w.close()
