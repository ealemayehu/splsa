# sPLSA

## Introduction

This project hosts the source code and dataset of the experiments specified in the paper "Supervised probabilistic latent semantic analysis (sPLSA) with applications to controversy analysis of legislative bills".

## Prerequisites

To execute the experiments, you will need to install Java 1.8 or later.

To execute the baseline, sLDA, you will need to install the following:

- The R software environment for statistical computing.
- The R "lda" package.

## Run Experiments

For a given _K_, _λ_, and _η_ values, the sPLSA experiments can be run by executing the following from the root directory of the project

```
cd java
java -jar main.jar -runModel -k K -lambda λ -eta η
```

Files with the following formats are generated as output in the `data/model` folder of this project

- `theta_{train|cv|test}_lλ_eη_kK_{initial|final}.txt`: Stores the ϴ matrix for the training (train), cross validation (cv) or test (test) datasets initially prior to training (initial) or after training (final).
- `beta_{train|cv|test}_lλ_eη_kK_{initial|final}.txt`: Stores the β matrix for the training (train), cross validation (cv) or test (test) datasets initially prior to training (initial) or after training (final).
- `v_{train|cv|test}_lλ_eη_kK_{initial|final}.txt`: Stores the v vector for the training (train), cross validation (cv) or test (test) datasets initially prior to training (initial) or after training (final).
- `perplexity_{train|cv|test}_lλ_eη_kK.txt`: Stores the perplexity values for the training (train), cross validation (cv) or test (test) datasets on a per iteration basis.
- `predicted_rmse_{cv|test}_lλ_eη_kK.txt`: Stores the RMSE for the predicted controversy scores for the cross validation (cv) or test (test) datasets.
- `top_words_{train|cv|test}_lλ_eη_kK.txt`: The top words on a per-latent topic basis for the training (train), cross validation (cv) or test (test) datasets.

## Run Baseline

For a given _K_ value, to run the sLDA baseline, do the following:

- Open the file `R/slda.r`, overwrite the K variable with the given _K_ value, and save the file.
- Invoke `R/slda.r` script in the R environment. This script generates plots that show the experimental results.

## Help

If you have any issues about running the experiments, please send an email to eyor.alemayehu@gmail.com
