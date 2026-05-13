from math import ceil
import os
import re

import pytest

import io
import soundfile as sf
import numpy as np

from config.settings import init_settings, get_settings
from service.helpers import *

def make_audio_bytes(duration: float, sample_rate: int = 22050, frequency: float = 440.0) -> bytes:
    """fixture to make a sin wave for the given duration with sample_rate*duration values"""
    samples = np.sin(2 * np.pi * frequency * np.linspace(0, duration, int(duration * sample_rate)))
    buffer = io.BytesIO()
    sf.write(buffer, samples, sample_rate, format='wav')
    return buffer.getvalue()

def make_silent_audio_bytes(duration: float, sample_rate: int = 22050, frequency: float = 440.0) -> bytes:
    """fixture to make a 0-valued array for the given duration with sample_rate*duration values"""
    samples = np.zeros(int(sample_rate * duration), dtype=np.float32)
    buffer = io.BytesIO()
    sf.write(buffer, samples, sample_rate, format='wav')
    return buffer.getvalue()

@pytest.fixture(autouse=True)
def env_file():
    sample_env_path = './test.env'
    with open(sample_env_path, 'w') as f:
        f.write("""
            MODEL_PATH=./ai-model/without_lyrics_cnn_weights.pth
                
            # Database
            DB_HOST=localhost
            DB_PORT=5432
            DB_NAME=postgres
            DB_USERNAME=postgres
            DB_PASSWORD=password

            # Broker
            BROKER_HOST=localhost
            BROKER_PORT=6379
        """)

    init_settings(sample_env_path)

    yield

    os.remove(sample_env_path)

class TestHelpers:
        
    # -------------- sample_file_bytes --------------
    def test_sample_file_bytes_correct_output_length(self):
        """Correctness test, ensures the output is sample_rate * duration length"""

        config = get_settings().spectrogram_config()

        # 60s sin wave
        audio_bytes = make_audio_bytes(2*config['duration'])

        result = sample_file_bytes(audio_bytes)
        expected = get_settings().spectrogram_config()['sample_rate'] * get_settings().spectrogram_config()['duration']
        assert len(result) == int(expected)

    def test_sample_file_bytes_short_audio(self):
        """Tests sample_file_bytes to ensure a short audio is not adjusted in size"""

        short_dur = get_settings().spectrogram_config()['duration'] - 5.0
        audio_bytes = make_audio_bytes(short_dur)

        result = sample_file_bytes(audio_bytes)
        expected = short_dur * get_settings().spectrogram_config()['sample_rate']
        assert len(result) == int(expected)
    
    def test_sample_file_bytes_peak_energy(self):
        """Tests sample_file_bytes correctly filters too long segments around the rms peak."""

        config = get_settings().spectrogram_config()
        sr, duration = config['sample_rate'], config['duration']
        total = int(3*sr*duration)
        
        audio = np.zeros(total, dtype=np.float32)
        burst_start = int(sr*duration+1)
        burst_end = int(burst_start + (0.5*sr))
        audio[burst_start:burst_end] = 1.0

        buffer = io.BytesIO()
        sf.write(buffer, audio, sr, format='wav')
        
        result = sample_file_bytes(buffer.getvalue())

        # burst 1.0 val should be contained (librosa sampling affects the exact value)
        assert np.abs(result).max() > 0.8

    def test_sample_file_bytes_silent_audio(self):
        """Tests sample_file_bytes rms function works with all zero values"""

        config = get_settings().spectrogram_config()
        audio_bytes = make_silent_audio_bytes(2*config['duration'])
        result = sample_file_bytes(audio_bytes)

        assert result is not None
        assert not np.any(np.isnan(result))

    # -------------- get_audio_hash --------------
    def test_get_audio_hash_valid(self):
        """Tests get_audio_hash for deterministic hashing and valid sha hash"""
        np_array = np.ones(4500)
        hash = get_audio_hash(np_array)
        assert hash == get_audio_hash(np_array)
        assert re.match('[a-f0-9]{64}', hash)

    def test_get_audio_hash_different_input(self):
        """Tests get_audio_hash for different inputs giving different outputs"""
        np_array_a = np.ones(30, dtype=np.float32)
        np_array_b = np_array_a.copy()
        np_array_b[15] = 3.0
        np_array_c = np.zeros(30)
        assert get_audio_hash(np_array_a) != get_audio_hash(np_array_b)
        assert get_audio_hash(np_array_a) != get_audio_hash(np_array_c)
        assert get_audio_hash(np_array_b) != get_audio_hash(np_array_c)

    # -------------- calc_spectrogram --------------
    def test_calc_spectrogram_valid(self):
        """
        Tests calc_spectrogram with the default usage.
        Any other usage is not expected, since it is only used to reduce similar code (the 2 spectrograms).
        This test is just to validate the usage is correct/isolating bugs if they exist
        """
        spect_config = get_settings().spectrogram_config()

        audio = np.ones(30)

        mel_spect = calc_spectrogram(audio, librosa.feature.melspectrogram, spect_config['mel'])
        cqt_spect = calc_spectrogram(audio, librosa.cqt, spect_config['cqt'])

        assert mel_spect.max() <= 1.0
        assert 0.0 <= mel_spect.min()
        assert spect_config['mel']['n_mels']== mel_spect.shape[0]
        assert cqt_spect.max() <= 1.0
        assert 0.0 <= cqt_spect.min()
        assert spect_config['cqt']['n_bins']== cqt_spect.shape[0]
    
    def test_calc_spectrogram_edge(self):
        """Tests calc_spectrogram when input is 0. Ensure the output does not produce infinity"""
        spect_config = get_settings().spectrogram_config()

        audio = np.zeros(30)

        mel_spect = calc_spectrogram(audio, librosa.feature.melspectrogram, spect_config['mel'])
        cqt_spect = calc_spectrogram(audio, librosa.cqt, spect_config['cqt'])

        assert not any(np.isinf(mel_spect))
        assert not any(np.isinf(cqt_spect))

    # -------------- mp3_to_spectrogram --------------
    def test_mp3_to_spectrogram_valid(self):
        """
        Tests mp3_to_spectrogram with valid input.
        Makes sure the output is padded, stacked, normalized, etc.
        """
        spect_config = get_settings().spectrogram_config()

        audio = np.ones(30)

        result = mp3_to_spectrogram(audio)

        length = max(spect_config['mel']['n_mels'], spect_config['cqt']['n_bins'])

        assert (2, length, ceil(spect_config['duration'] * spect_config['sample_rate']/spect_config['hop_length'])) == result.shape
        assert np.float32 == result.dtype
        assert not np.allclose(result[0], result[1])
        assert 0.0 <= result.min()
        assert 1.0 >= result.max()