# AI Genre
## Introduction
This repo tests song classifying by running spectrograms and lyrics through CNNs.

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
pip uninstall torch torchvision
pip cache purge
pip install torch torchvision
```
You can check the result with the following command: `python -c "import torch; print(torch.__version__)"`, which should output `{torch_version}+cpu`