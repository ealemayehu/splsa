# Train and test SLDA model.

setwd("../data/dataset")
source("trainingSldaDataset.txt");
trainingCS = read.csv("trainingControversyScores.txt", header=FALSE)
trainingCS = as.vector(trainingCS[[1]])
vocab = read.csv("vocabulary.txt", header=FALSE)
vocab = as.vector(vocab[[1]])
K = 40
num.e.iterations = 20
num.m.iterations = 20
alpha = 0.1
eta = 0.1
library(lda)
params=sample(c(-1, 1), K, replace=TRUE)
variance=0.25
sldaResult = slda.em(
        documents=trainingList, 
        K=K, 
        vocab=vocab, 
        num.e.iterations=num.e.iterations, 
        num.m.iterations=num.m.iterations, 
        alpha=alpha,
        eta=eta,
        annotations=trainingCS,
        params=params,
        lambda=1.0,
        variance=variance,
        logistic=FALSE,
        trace=2L,
		method="sLDA"
)
source("testingSldaDataset.txt");
testingCS = read.csv("testingControversyScores.txt", header=FALSE)
testingCS = as.vector(testingCS[[1]])
sldaPrediction = slda.predict(
        documents=testingList,
        sldaResult$topics,
        sldaResult$model,
        alpha=alpha,
        eta=eta,
        trace=2L)
sldaPrediction = as.vector(sldaPrediction)
library(hydroGOF)
rmse(sldaPrediction, testingCS)

# Analyze results.

theta = t(t(sldaResult$document_sums)/colSums(sldaResult$document_sums))
maxTheta = apply(theta, 2, max)
maxThetaIndexes = apply(theta, 2, which.max)
cSum = numeric(K)
cCount = numeric(K)

for(d in 1:length(maxTheta)) {
	i = maxThetaIndexes[d]
	cSum[i] = cSum[i] + trainingCS[d]
	cCount[i] = cCount[i] + 1
}

cAverage = cSum / cCount

quartz.options(width=4, height=4)
quartz.options(pointsize=12)
plot(sldaResult$coefs, cAverage, xlab="Coefficient of each topic", 
     ylab="Average controversy score when topic is most probable", pch=19)
cor(sldaResult$coefs, cAverage);
     
weightedSum = numeric(K)
weightSum = numeric(K)

for(k in 1:K) {
	for(d in 1:length(maxTheta)) {
		weightedSum[k] = weightedSum[k] + theta[k,d] * trainingCS[K]
		weightSum[k] = weightSum[k] + theta[k,d]
	}
}

wAverage = weightedSum / weightSum
plot(sldaResult$coefs, wAverage, xlab="Coefficient of each topic", 
     ylab="Average weighted controversy score of each topic", pch=19)

sortedTheta = apply(theta, 2, sort, decreasing=TRUE)
avgPerStatistic = apply(sortedTheta, 1, mean)
plot(1:10, avgPerStatistic, xlab="Statistic", ylab="Average probability", pch=19)

# Print the top ten words in each topic.

apply(top.topic.words(sldaResult$topics, 10, by.score=TRUE),
      2, paste, collapse=" ")
