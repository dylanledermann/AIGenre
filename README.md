# AI Genre
## Introduction
This repo tests song classifying by running spectrograms and lyrics through CNNs using the [Kaggle FMA Dataset](https://www.kaggle.com/datasets/imsparsh/fma-free-music-archive-small-medium/data).

## Getting Started
To start, create a python environment (I used conda) and install the required modules.
The current python that conda allows at the time of creating this is 3.14.
```bash
conda create -n ai-genre python=3.14
pip install -r requirements.txt
```
This project uses CUDA 12.6 for GPU processing. 
If you have CUDA, but a different version, uninstall and reinstall the correct torch version from [here](https://pytorch.org/get-started/locally/).
If you do not have CUDA and can not download it, run the following to change the torch and torchvision to cpu:
```bash
pip uninstall torch torchvision torchaudio
pip cache purge
pip install torch torchvision torchaudio
```
You can check the result with the following command: `python -c "import torch; print(torch.__version__)"`, which should output `{torch_version}+cpu`

## Layout
This repository is split into different .ipynb files containing different models and custom datasets.

## Results
The ResNet model was able to achieve over 70% with and without lyrics. Adding lyrics yielded an increase of 2% in the final accuracy. In the ResNet testing dataset, the most common error occurred through mixing up Easy-Listening and Pop genres, which both 
| | Without Lyrics Accuracy | With Lyrics Accuracy |
| --- | :---: | :---: |
| ResNet 18 | 70.60% | 72.01% |