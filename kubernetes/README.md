# Organization
This directory is organized by service similar to the main file structure.
└──
```test
kubernetes/
├─ ai_genre_frontend/
|  └── 
├─ ai-genre/
|
├─ celery_worker/
|  ├─ celery/
|  |
|  └── grpc/
└── storage/
```

# Deployment
Each application has a deployment/manifest to create all the pods for the application.
Once you have a kubernetes control plane set up, you can run kubectl apply -f <file/directory> on each of the .yaml files for each directory.
Each service requires secrets, which can be found from default environment variables for the image or the created services configs files/application properties.
To add the .env files to each node create the secrets `kubectl create secret generic <secret name> --from-env-file=<path to .env>`.
Make sure to edit the name of the secrets for each deployment (they inject the secrets through the name of the secret).