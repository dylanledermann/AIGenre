from typing import Optional

import torch
import torch.nn as nn
class PatchEmbedding(nn.Module):
    """
    Splits Spectrogram into patches the size of the embedding dimension
    """
    def __init__(
        self,
        in_channels: int = 2,
        patch_size: int = 16,
        embed_dim: int = 768,
        img_size: tuple = (128, 1292)
    ): 
        super().__init__()

        self.num_patches = (img_size[0]//patch_size) * (img_size[1]//patch_size)

        # Conv2d as patch extractor
        self.projection = nn.Conv2d(
            in_channels,
            embed_dim,
            kernel_size=patch_size,
            stride=patch_size
        )
    
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x - (batch, channels, H, W    )
        x = self.projection(x) # x - (batch, embed_dim, H/patch_size, W/patch_size)
        x = x.flatten(2) # x - (batch, embed_dim, num_patches)
        x = x.transpose(1, 2) # x - (batch, num_patches, embed_dim)
        return x
    
class SpectrogramTransformer(nn.Module):
    """
    Spectrogram and Lyric transformer. Made to work with BERT encoder for lyrics (1x768 tokens)
    """
    def __init__(
        self,
        num_classes: int,
        in_channels: int = 2,
        img_size: tuple = (128, 1292),
        patch_size: int = 16,
        embed_dim: int = 768,
        num_heads: int = 12,
        num_layers: int = 6,
        mlp_dim: int = 2048,
        dropout: float = 0.1
    ):
        super().__init__()

        # Patch Embedding
        self.patch_embedding = PatchEmbedding(in_channels, patch_size, embed_dim, img_size)

        num_patches = self.patch_embedding.num_patches

        self.cls_token = nn.Parameter(torch.zeros(1, 1, embed_dim))

        # number of patches + 1 for [CLS] token
        self.pos_embed = nn.Parameter(torch.zeros(1, num_patches+1, embed_dim))

        self.lyric_embed = nn.Parameter(torch.zeros(1, 1, embed_dim))
        self.lyric_projection = nn.Linear(768, embed_dim)

        self.dropout = nn.Dropout(p=dropout)

        # Transformer encoder
        encoder_layer = nn.TransformerEncoderLayer(
            d_model = embed_dim,
            nhead = num_heads,
            dim_feedforward = mlp_dim,
            dropout = dropout,
            activation = 'gelu',
            batch_first = True, # (batch, seq, features) ordering
            norm_first = True #pre-norm
        )

        self.transformer = nn.TransformerEncoder(
            encoder_layer,
            num_layers,
            enable_nested_tensor=False
        )

        self.norm = nn.LayerNorm(embed_dim)

        self.classifier = nn.Sequential(
            nn.Linear(embed_dim, 256),
            nn.GELU(),
            nn.Dropout(p=dropout),
            nn.Linear(256, num_classes)
        )

        nn.init.trunc_normal_(self.cls_token, std=0.2)
        nn.init.trunc_normal_(self.pos_embed, std=0.2)
        self._init_weights()
    
    def _init_weights(self):
        for module in self.modules():
            if isinstance(module, nn.Linear):
                nn.init.trunc_normal_(module.weight, std=0.2)
                if module.bias is not None:
                    nn.init.zeros_(module.bias)
            elif isinstance(module, nn.LayerNorm):
                nn.init.ones_(module.weight)
                nn.init.zeros_(module.bias)

    def forward(self, x: torch.Tensor, lyrics: Optional[torch.Tensor] = None) -> torch.Tensor:
        batch_size = x.shape[0]

        x = self.patch_embedding(x) # x - (batch_size, num_patches, embed_dim)

        # Prepend cls token to the front of x
        cls_token = self.cls_token.expand((batch_size, -1, -1)) # cls_token - (batch_size, 1, embed_dim)
        x = torch.cat([cls_token, x], dim=1) # x - (batch_size, num_patches + 1, embed_dim)
        pos_embed = self.pos_embed
        if lyrics is not None:
            lyrics = self.lyric_projection(lyrics)
            x = torch.cat([x, lyrics], dim=1)
            pos_embed = torch.cat([pos_embed, self.lyric_embed], dim=1)

        # Add positional embedding
        x += pos_embed

        # Transformer Encoder
        x = self.dropout(x)
        x = self.transformer(x)
        x = self.norm(x)

        cls_output = x[:, 0, :]

        return self.classifier(cls_output)