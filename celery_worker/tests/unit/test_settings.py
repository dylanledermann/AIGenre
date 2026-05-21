import pytest
import os
from src.config.settings import init_settings, get_settings

@pytest.fixture
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

    yield sample_env_path

    os.remove(sample_env_path)

class TestSettings:
    def test_load_settings(self, env_file):
        """
        Test to make sure Settings class correctly loads when given env file path
        """

        init_settings(env_file)

        settings = get_settings()

        # Model
        assert settings.model_path == './ai-model/without_lyrics_cnn_weights.pth'

        # Database
        assert settings.db_host == 'localhost'
        assert settings.db_port == 5432
        assert settings.db_user == 'postgres'
        assert settings.db_user == 'postgres'
        assert settings.db_pass == 'password'

        # Broker
        assert settings.backend_host == 'localhost'
        assert settings.backend_port == 6379