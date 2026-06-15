import os
from typing import Optional
from dotenv import load_dotenv
import librosa
import urllib3

_settings = None

def get_settings():
    return _settings

def init_settings():
    global _settings
    _settings = Settings()

class Settings:
    def __init__(self):

        # Model
        self.model_path = os.getenv('MODEL_PATH')
        self.model_dropout = float(os.getenv('MODEL_DROPOUT', '0.5'))
        self.model_in_channels = int(os.getenv('MODEL_IN_CHANNELS', '2'))
        self.model_num_classes = int(os.getenv('MODEL_NUM_CLASSES', '16'))

        # Spectrogram
        self.spect_sample_rate = int(os.getenv('SPECTROGRAM_SAMPLE_RATE', '22050'))
        self.spect_duration = float(os.getenv('SPECTROGRAM_DURATION', '30'))
        self.spect_n_mels = int(os.getenv('SPECTROGRAM_N_MELS', '128'))
        self.spect_n_bins = int(os.getenv('SPECTROGRAM_N_BINS', '64'))
        self.spect_bins_per_octave = int(os.getenv('SPECTROGRAM_BINS_PER_OCTAVE', '12'))
        self.spect_n_fft = int(os.getenv('SPECTROGRAM_N_FFT', '2048'))
        self.spect_hop_length = int(os.getenv('SPECTROGRAM_HOP_LENGTH', '512'))

        # Database
        self.db_host = os.getenv('DB_HOST')
        self.db_port = int(os.getenv('DB_PORT'))
        self.db_name = os.getenv('DB_NAME')
        self.db_user = os.getenv('DB_USERNAME')
        self.db_pass = os.getenv('DB_PASSWORD')

        self.min_size = int(os.getenv('MIN_POOL', '1'))
        self.max_size = int(os.getenv('MAX_POOL', '5'))

        # Backend
        self.backend_host = os.getenv('BACKEND_HOST')
        self.backend_port = int(os.getenv('BACKEND_PORT'))
        self.backend_db = os.getenv('BACKEND_DB', '0')

        # Minio object storage
        self.minio_endpoint = os.getenv('MINIO_HOST')
        self.minio_access_key = os.getenv('MINIO_ROOT_USER')
        self.minio_secret_key = os.getenv('MINIO_ROOT_PASSWORD')
        # secure flag in minio indicates whether https or http (Since this is using docker containers interconnections secure is false)
        self.minio_secure = os.getenv('MINIO_SECURE', 'False').lower() == "true"
        self.minio_cert = os.getenv('MINIO_CA_CERT')

    def model_config(self) -> dict[str, str | int | float]:
        return {
            'path': self.model_path,
            'dropout': self.model_dropout,
            'in_channels': self.model_in_channels,
            'num_classes': self.model_num_classes
        }
    
    def spectrogram_config(self) -> dict[str, float | int]:
        return {
            'sample_rate': self.spect_sample_rate,
            'duration': self.spect_duration,
            'hop_length': self.spect_hop_length,
            'mel': {
                'sr': self.spect_sample_rate,
                'n_mels': self.spect_n_mels,
                'n_fft': self.spect_n_fft,
                'hop_length': self.spect_hop_length,
            },
            'cqt': {
                'sr': self.spect_sample_rate,
                'n_bins': self.spect_n_bins,
                'bins_per_octave': self.spect_bins_per_octave,
                'hop_length': self.spect_hop_length,
                'fmin': librosa.note_to_hz('C1')
            }
        }
    
    def database_config(self) -> dict[str, str]:
        return {
            'kwargs': {
                'host': self.db_host,
                'port': self.db_port,
                'dbname': self.db_name,
                'user': self.db_user,
                'password': self.db_pass,
            },
            'min_size': self.min_size,
            'max_size': self.max_size
        }
    
    def backend_config(self) -> dict[str, str]:
        return {
            'host': self.backend_host,
            'port': self.backend_port,
            'db': self.backend_db,
        }
    
    def minio_config(self) -> dict[str, str]:
        config = {
            'endpoint': self.minio_endpoint,
            'access_key': self.minio_access_key,
            'secret_key': self.minio_secret_key,
            'secure': self.minio_secure
        }
        # Allow for cert or no cert
        if self.minio_cert:
            config['http_client'] = urllib3.PoolManager(ca_certs=self.minio_cert)
        return config