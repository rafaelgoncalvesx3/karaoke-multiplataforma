package br.com.karaoketv;

/** YIN cumulative mean normalized difference; pitch in Hz, 0 for unvoiced. */
public final class Yin {
    private Yin() {}
    public static double detect(short[] pcm, int length, int sampleRate) {
        if (length < 1024 || sampleRate <= 0) return 0;
        double rms = 0, mean = 0;
        for (int i=0; i<length; i++) mean += pcm[i];
        mean /= length;
        for (int i=0; i<length; i++) { double v = (pcm[i]-mean) / 32768d; rms += v*v; }
        if (Math.sqrt(rms/length) < 0.012) return 0;
        int minTau = Math.max(2, sampleRate / 1050);
        int maxTau = Math.min(sampleRate / 75, length/2 - 1);
        if (maxTau <= minTau) return 0;
        double[] difference = new double[maxTau+1], cmnd = new double[maxTau+1];
        for (int tau=1; tau<=maxTau; tau++) {
            double sum=0;
            for (int i=0; i<length-maxTau; i++) {
                double d = (pcm[i]-pcm[i+tau])/32768d;
                sum += d*d;
            }
            difference[tau]=sum;
        }
        cmnd[0]=1;
        double running=0;
        for (int tau=1; tau<=maxTau; tau++) {
            running += difference[tau];
            cmnd[tau] = running == 0 ? 1 : difference[tau] * tau / running;
        }
        int chosen=-1;
        for (int tau=minTau; tau<=maxTau; tau++) {
            if (cmnd[tau] < 0.16) {
                while (tau+1<=maxTau && cmnd[tau+1]<cmnd[tau]) tau++;
                chosen=tau; break;
            }
        }
        if (chosen < 0 || cmnd[chosen] > 0.23) return 0;
        double refined = chosen;
        if (chosen > 1 && chosen < maxTau) {
            double a=cmnd[chosen-1], b=cmnd[chosen], c=cmnd[chosen+1];
            double denominator = 2*(a-2*b+c);
            if (Math.abs(denominator)>1e-12) refined += Math.max(-1, Math.min(1, (a-c)/denominator));
        }
        return refined > 0 ? sampleRate/refined : 0;
    }
}
