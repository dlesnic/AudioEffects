package com.lesnic.licenta.app.audio_effects;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.lesnic.licenta.app.sound_gui.AudioGui;
import com.lesnic.licenta.app.utils.EqSizes;

/**
 * Create a instance for 16 bit Wav audio manipulation (time domain)
 * 
 */
public class AudioManipulation {
    public static final int SOUND_BUFFER = 8192;
    private FFTImpl fft = new FFTImpl();
    private EqSizes eqSizes = new EqSizes();
    WavArrays wavArrays = WavArrays.getInstance();
    public static final int[] KERNEL = { 1, 3, 7, 9, 21, 9, 7, 3, 1 };

    /**
     * Writes audio data to the mixer via the source data line. The requested
     * number of bytes of data are read from the sampleArrayWav(selected sound),
     * starting at the given offset into the array, and written to the data
     * line's buffer
     * 
     * @throws IOException
     */
    public void playWav() throws IOException {
        AudioInputStream audioIn = wavArrays.getAudioIn();
        AudioFormat audioFormat = audioIn.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class,
                audioFormat);
        SourceDataLine audioLine = null;
        // reverbEffect(500, 0.6f, audioFormat.getSampleRate());
        // get the current byte array
        byte[] inputPlay = wavArrays.getSampleArrayWav();
        byte[] audioBuffer = new byte[SOUND_BUFFER];

        double[] fftOutput;
        short[] ifftOutput;
        short[] iR = new short[SOUND_BUFFER / 2];
        getIR(iR);

        try {

            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(audioFormat);
            audioLine.start();
            for (int i = 0; i < inputPlay.length / SOUND_BUFFER; i++) {

                for (int j = 0; j < SOUND_BUFFER; j++) {
                    audioBuffer[j] = inputPlay[(i * SOUND_BUFFER) + j];
                }
                computeWavSamples(audioBuffer);
                // multiplyArr(wavArrays.getBitArrayWav(), iR);
                fftOutput = fft.calcFFT(wavArrays.getBitArrayWav());
                fft.calcMangFreq(fftOutput);
                ifftOutput = fft.calcInverseFFT(fftOutput);
                samplesToByte(ifftOutput);
                audioBuffer = wavArrays.getSampleArrayWav();
                audioLine.write(audioBuffer, 0, audioBuffer.length);
                AudioGui.getEqLine31().setHeight(fft.getMagnitude()[1] / 2);
                AudioGui.getEqLine63().setHeight(fft.getMagnitude()[2] / 2);
                AudioGui.getEqLine125().setHeight(fft.getMagnitude()[4] / 2);
                AudioGui.getEqLine250().setHeight(fft.getMagnitude()[8] / 2);
                AudioGui.getEqLine500().setHeight(fft.getMagnitude()[16] / 2);
                AudioGui.getEqLine1k().setHeight(fft.getMagnitude()[32] / 2);
                AudioGui.getEqLine2k().setHeight(fft.getMagnitude()[64] / 2);
                AudioGui.getEqLine4k().setHeight(fft.getMagnitude()[128] / 2);
                AudioGui.getEqLine8k().setHeight(fft.getMagnitude()[256] / 2);
                AudioGui.getEqLine16k().setHeight(fft.getMagnitude()[500] / 2);

            }
            // audioLine.write(inputPlay, 0, inputPlay.length);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        } finally {
            audioLine.drain();
            audioLine.close();
            audioIn.close();
        }

    }

    /**
     * Obtains an audio input stream from the provided File and stores it in
     * audioIn. The File must point to valid audio file data.Reads some number
     * of bytes from the audio input stream and stores them into
     * originalArrayWav and sampleArrayWav.
     * 
     * @param Wav
     *            file
     */
    public void setByteArray(File soundFile) {
        int byteArraySize;

        byte[] audioArray;

        // length of the stream, expressed in sample frames rather than bytes.
        int frameLenght;

        // the number of bytes per frame
        int frameSize;

        try {

            AudioInputStream audioIn = AudioSystem
                    .getAudioInputStream(soundFile);
            AudioFormat audioFormat = audioIn.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class,
                    audioFormat);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem
                    .getLine(info);
            audioLine.open(audioFormat);

            audioLine.start();
            frameLenght = (int) audioIn.getFrameLength();
            frameSize = audioFormat.getFrameSize();
            byteArraySize = frameLenght * frameSize;
            audioArray = new byte[byteArraySize];
            audioIn.read(audioArray);

            // set current byte array and audio input stream from the provided
            // File
            wavArrays.setSampleArrayWav(audioArray);
            wavArrays.setAudioIn(audioIn);

            audioLine.drain();
            audioLine.close();
            audioIn.close();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        }
    }

    // private void covolutin(short sample) {
    // }

    /**
     * Obtains the size of a sample, get the number of bits and stores them into
     * bitArrayWav
     * 
     * @param byte array with Wav samples
     */
    public void computeWavSamples(byte[] inputArray) {
        AudioInputStream audioIn = wavArrays.getAudioIn();
        AudioFormat audioFormat = audioIn.getFormat();
        int bitPerSamples = audioFormat.getSampleSizeInBits();
        int audioArraySize = inputArray.length;
        short[] bit16Array = new short[audioArraySize / 2];
        short[] bit8Array = new short[audioArraySize];
        if (bitPerSamples == 16) {
            System.out.println(audioFormat);
            if (audioFormat.isBigEndian()) {
                for (int i = 0; i < audioArraySize / 2; i++) {
                    bit16Array[i] = getSampleBigEndian(inputArray, i);
                }
                wavArrays.setBitArrayWav(bit16Array);

            } else {
                for (int i = 0; i < audioArraySize / 2; i++) {
                    bit16Array[i] = getSampleLittleEndian(inputArray, i);
                }
                wavArrays.setBitArrayWav(bit16Array);
            }
        } else if (bitPerSamples == 8) {
            for (int index = 0; index < audioArraySize; index++) {

                bit8Array[index] = (short) (inputArray[index]);
                System.out.println(bit8Array[index]);
            }
            wavArrays.setBitArrayWav(bit8Array);

        } else {
            System.out.println("Nu este implementat pentru: " + bitPerSamples
                    + " bit");
        }

    }

    public void multiplyArr(short[] audio, short[] iR) {
        for (int i = 0; i < iR.length; i++) {
            audio[i] *= iR[i];
        }
    }

    public void getIR(short[] iR) {
        int i = 0;
        Scanner scan;
        File file = new File("ir.txt");
        try {
            scan = new Scanner(file);

            while (scan.hasNextDouble()) {
                iR[i] = (short) scan.nextDouble();
                i++;
            }

        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        }

    }

    public void reverbEffect(double delay, float decay, float sampleRate) {

        short[] inputArray = wavArrays.getBitArrayWav();
        // assumes 44100 Hz sample rate
        int delaySamples = (int) ((float) delay * sampleRate);
        for (int i = 0; i < inputArray.length - delaySamples; i++) {
            // WARNING: overflow potential
            inputArray[i + delaySamples] += (short) ((float) inputArray[i] * decay);
        }
        samplesToByte(inputArray);
    }

    public void echoEffect(byte[] inputArray, double delay, float decay) {
        int delayBufferPos = 0;

        int length = inputArray.length;
        int delayBufferSize = (int) (44100 * delay);
        short[] delayBuffer = new short[delayBufferSize];
        for (int i = 0; i < length; i += 2) {
            // update the sample
            short oldSample = getSampleLittleEndian(inputArray, i);
            short newSample = (short) (oldSample + decay
                    * delayBuffer[delayBufferPos]);
            setSampleLittleEndian(wavArrays.getSampleArrayWav(), i, newSample);

            // update the delay buffer
            delayBuffer[delayBufferPos] = newSample;
            delayBufferPos++;
            if (delayBufferPos == delayBuffer.length) {
                delayBufferPos = 0;
            }
        }
    }

    public void samplesToByte(short[] samples) {
        byte[] byteArray = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            setSampleLittleEndian(byteArray, i, samples[i]);
        }
        wavArrays.setSampleArrayWav(byteArray);
    }

    public static short getSampleLittleEndian(byte[] buffer, int position) {
        return (short) (((buffer[2 * position + 1] & 0xff) << 8) | (buffer[2 * position] & 0xff));
    }

    public static void setSampleLittleEndian(byte[] buffer, int position,
            short sample) {
        buffer[2 * position] = (byte) (sample);
        buffer[2 * position + 1] = (byte) (sample >> 8);

    }

    public static short getSampleBigEndian(byte[] buffer, int position) {
        return (short) (((buffer[position] & 0xff) << 8) | (buffer[position + 1] & 0xff));
    }

    public static void setSampleBigEndian(byte[] buffer, int position,
            short sample) {
        buffer[position + 1] = (byte) (sample & 0xff);
        buffer[position] = (byte) ((sample >> 8) & 0xff);
    }
}
