from typing import Callable

import librosa

import hashlib
import io

import torch
import numpy as np
from torchvision import transforms

from src.config.settings import get_settings
from src.ai_model.model import get_model

idxToGenre = [
    'Blue',
    'Classical',
    'Country',
    'Easy Listening',
    'Electronic',
    'Experimental',
    'Folk',
    'Hip-Hop',
    'Instrumental',
    'International',
    'Jazz',
    'Old-Time / Historic',
    'Pop',
    'Rock',
    'Soul-RnB',
    'Spoken'
]

def sample_file_bytes(file_bytes: bytes) -> np.ndarray:
    spect_config = get_settings().spectrogram_config()
    audio, _ = librosa.load(io.BytesIO(file_bytes), sr=spect_config['sample_rate'], mono=True)

    rms = librosa.feature.rms(y=audio)

    num_frames = int(spect_config['duration'] * spect_config['sample_rate'] / spect_config['hop_length'])

    if rms.shape[1] > num_frames:
        energy_sum = np.convolve(rms[0], np.ones(num_frames), 'valid')
        best_frame = np.argmax(energy_sum)
        start_sample = int(best_frame * spect_config['hop_length'])
    else:
        start_sample = 0

    end_sample = start_sample + int(spect_config['duration'] * spect_config['sample_rate'])
    return audio[start_sample:end_sample]

def get_audio_hash(audio: np.ndarray) -> str:
    hash_object = hashlib.sha256(audio.tobytes())
    return hash_object.hexdigest()

def calc_spectrogram(audio_array: np.ndarray, func: Callable, config: dict) -> np.ndarray:
    spect = func(y=audio_array, **config)

    log_spect = librosa.amplitude_to_db(np.abs(spect), ref=np.max)
    norm_log_spect = (log_spect - log_spect.min()) / (log_spect.max() - log_spect.min() + 1e-8)

    return norm_log_spect


def mp3_to_spectrogram(audio: np.ndarray) -> np.ndarray:
    # ToDo implement mel and cqt configs
    spect_config = get_settings().spectrogram_config()

    expected_samples = int(spect_config['sample_rate'] * spect_config['duration'])
    if expected_samples > len(audio):
        audio = np.pad(audio, (0, expected_samples - len(audio)))

    mel_spect = calc_spectrogram(audio, librosa.feature.melspectrogram, spect_config['mel'])
    cqt_spect = calc_spectrogram(audio, librosa.cqt, spect_config['cqt'])
    # Pad if the spects are different sizes
    diff = spect_config['mel']['n_mels'] - spect_config['cqt']['n_bins']
    mel_spect = np.pad(mel_spect, ((0, max(0, -diff)), (0, 0)))
    cqt_spect = np.pad(cqt_spect, ((0, max(0, diff)), (0, 0)))

    spectrograms = np.stack([mel_spect.astype(np.float32), cqt_spect.astype(np.float32)], axis=0)

    return spectrograms

def run_analysis(spectrogram: np.ndarray) -> tuple[str, str]:
    get_model().eval()

    torch_input = torch.tensor(spectrogram, dtype=torch.float32, device = 'cuda' if torch.cuda.is_available() else 'cpu').unsqueeze(0)

    transform = transforms.Compose([
        transforms.Normalize(
            mean=[0.4647507724693225, 0.3520177680340939], 
            std=[0.17030458340764293, 0.29191792208277895]
        )
    ])
    normalized_input = transform(torch_input)

    out = get_model()(normalized_input)

    probs = torch.softmax(out, -1)
    genre, accuracy = torch.argmax(probs), torch.max(probs)
    # Convert genre idx to string
    return idxToGenre[genre.item()], str(accuracy.item())