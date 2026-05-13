import torchvision
import torch

_model = None

def get_model():
    return _model

def build_model(config: dict) -> None:
    global _model

    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    _model = torchvision.models.resnet18(weights=torchvision.models.ResNet18_Weights.DEFAULT)
    _model.conv1 = torch.nn.Conv2d(config.get("in_channels", 2), 64, kernel_size=7, stride=2, padding=3, bias=False)
    _model.fc = torch.nn.Sequential(
        torch.nn.Dropout(p=config.get("dropout", 0.5)),
        torch.nn.Linear(_model.fc.in_features, config.get("num_classes", 16))
    )

    _model.load_state_dict(config['path'])
    _model = _model.to(device)
    _model.eval()