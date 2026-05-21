# AI Genre
## Introduction
This repo tests song classifying by running spectrograms and lyrics through CNNs using the [Kaggle FMA Dataset](https://www.kaggle.com/datasets/imsparsh/fma-free-music-archive-small-medium/data).

## Getting Started
To start, create a python environment (I used conda) and install the required modules.
The current python that conda allows at the time of creating this is 3.14.

***Disclaimer*** The requirement.txt in the home directory, `./requirements.txt`, contains only the packages for the .ipynb model trainings. The application (celery and grpc) packages can be found in the `./celery_worker` directory with the exception of torch.
The torch download instructions are below.
```bash
conda create -n ai-genre python=3.14
pip install -r requirements.txt
```
### Torch
PyTorch is not included in the requirements.txt, since there are difference versions depending on if you are using CUDA, the CUDA versions, etc.
If you have an Nvidia GPU, you can download PyTorch with CUDA from [here](https://pytorch.org/get-started/locally/).
Otherwise, you can use the PyTorch with CPU:
```bash
pip install torch torchvision torchaudio
```
You can check the result with the following command: `python -c "import torch; print(torch.__version__)"`, which should output `{torch_version}+cpu`

## Layout
This repository is split into different .ipynb files containing different models and custom datasets as well as the website implementation.

## Results
The ResNet model was able to achieve over 70% with and without lyrics. The transformer models struggled to reach 70% accuracy, most likely due to lacking data. While training the transformer both would coverge to around 72%. After adding lyrics, it was determined that since the lyrics were generated using another AI model (whisper), the lyrics input were unnecessary complexity. With the lyrics the model would have to learn the spectrograms and the possible connections made by the lyric model, which if it just generated words, would have little, or negative, impact. The main error point in all models was mixing up easy-listening and pop.
| Model | Without Lyrics Accuracy | Without Lyrics Loss | With Lyrics Accuracy | With Lyrics Loss
| --- | :---: | :---: | :---: | :---: |
| ResNet 18 | 73.53% | 1.0215 | 72.01% | 1.0167
| Generic Transformer | 67.95% | 1.1174 | 70.60% | 1.0636

# Website
The website was created using SpringBoot for the backend (./ai-genre), React for the frontend (./ai_genre_frontend), and a Celery worker with a FastAPI entrypoint(celery_worker). Each service has its own README.md file that indicates how to run each. 