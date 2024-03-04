import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONException;

import weka.core.matrix.LinearRegression;
import weka.core.matrix.Matrix;

public class Model
{
	private static final double[] LAMBDAS = new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
	private static final int[] KS = new int[]{10};
	private static final double[] ETAS = new double[]{1};
	private static boolean SINGLE_RUN = false;
	private static final int MAX_ITERATIONS = 100;
	private static final int MAX_THETA_ITERATIONS = 20;
	private static final int MAX_V_ITERATIONS = 100;
	private static final double INITIAL_STEP_SIZE = 500;
	private static final double CONVERGENCE_STEP_SHRINKAGE_RATE = 0.9;
	private static final double TRAINING_CONVERGENCE_RATE = 1E-2;
	private static final double TESTING_CONVERGENCE_RATE = 1E-2;
	private static final double EPSILON = 0.01;
	private static final double SMOOTH_VALUE = 1.0E-80;
	private static final String MODEL_PERSISTENCE_DIRECTORY = getModelPersistenceDirectory();
	private static final int ITERATION_DUMP_COUNT = 200;
	private static final double CONVERGENCE_DIFFERENCE = 1E-10;
	private List<List<WordInfo>> documents;
	private Matrix c;
	private Matrix v;
	private Matrix beta;
	private Matrix theta;
	private Matrix thetaWithBias;
	private int[] lastMuColumns;
	private Matrix mu;
	private int N;
	private int W;
	private int K;
	private double eta;
	private Double[] thetaTimesVCache;
	private Double[] thetaTimesVSquaredCache;
	private double[][] e_kw;
	private double[] e_k;
	private double[][] e_dk;
	private int totalWordCount;
	private double step;
	private boolean converged;
	private double convergenceRate;
	private double previousLikelihood;
	private String[] words;
	private double lambda;
	private Purpose purpose;
	private double perplexity;
	private ModelType modelType = ModelType.LINEAR;

	private enum Purpose
	{
		TRAINING, CROSS_VALIDATION, TESTING
	}
	
	private enum ModelType
	{
		LINEAR,
		QUADRATIC,
		SIGMOID
	}
	
	private static String getModelPersistenceDirectory() {
		String directory = System.getProperty("model.persistance.directory");
		
		if(directory == null)
		{
			directory = "../data/model";
		}
	
		return directory;
	}

	private Model(List<List<WordInfo>> documents,
	      Map<String, Integer> vocabulary, List<Double> controversyScores,
	      double lambda, int K, double eta, Matrix beta, Matrix v,
	      Purpose purpose) throws IOException
	{
		assert (documents.size() == controversyScores.size());

		this.step = INITIAL_STEP_SIZE;
		this.previousLikelihood = Double.MIN_VALUE;
		this.documents = documents;
		this.N = documents.size();
		this.W = vocabulary.size();
		this.K = K;
		this.eta = eta;
		this.theta = new Matrix(N, K);
		this.thetaWithBias = new Matrix(N, K + 1, 1);
		this.mu = new Matrix(N, K);
		this.lastMuColumns = new int[N];
		this.beta = beta == null ? new Matrix(K, W) : beta;
		this.v = v;
		this.thetaTimesVCache = new Double[N];
		this.thetaTimesVSquaredCache = new Double[N];
		this.e_kw = new double[K][W];
		this.e_dk = new double[N][K];
		this.e_k = new double[K];
		this.lambda = lambda;
		this.c = toMatrix(controversyScores);		

		this.purpose = purpose;

		switch (purpose)
		{
			case TRAINING:
				log("Training mode");
				break;

			case CROSS_VALIDATION:
				log("Cross validation mode");
				break;

			case TESTING:
				log("Testing mode");
				break;
		}

		calculateTotalWordCount();
		words = DatasetPreparer.getWordsAsArray(vocabulary);
		initializeLastMuColumns();

		log("Stats: N = " + N + ", W = " + W + ", K = " + K
		      + ", Average unique words per document: "
		      + averageNumberOfUniqueWordsPerDocument());
		log("Stats: Total word count: " + totalWordCount);

		gaussianRandomizeMatrix(mu);

		if(purpose == Purpose.TRAINING)
		{
			convergenceRate = Model.TRAINING_CONVERGENCE_RATE;

			initializeStochasticMatrix(this.beta);
		}
		else
		{
			convergenceRate = Model.TESTING_CONVERGENCE_RATE;
		}

		updateTheta();

		initializeProgressFiles();

		saveMatrix("beta", "initial", this.beta);
		saveMatrix("theta", "initial", this.theta);
		saveTopWords("initial");

		for (int i = 1; i <= MAX_ITERATIONS && !converged; i++)
		{
			log("Iteration " + i);
			log("Eta = " + eta);

			printMatricesInfo();

			assertStochasticMatrix(this.theta);
			assertStochasticMatrix(this.beta);

			clearCache();

			if(purpose == Purpose.TRAINING)
			{
				optimizeV();
				optimizeBeta();
			}
			else
			{
				eStep();
				postEMStep();
			}

			optimizeTheta();

			double rmse = computeRmse();

			double currentLikelihood = likelihood();

			double difference = Math.abs(currentLikelihood - previousLikelihood);

			converged = difference < CONVERGENCE_DIFFERENCE;
			previousLikelihood = currentLikelihood;
			saveProgress(rmse, currentLikelihood);

			double rate = Math.abs(difference / previousLikelihood);

			if(rate < convergenceRate)
				step *= CONVERGENCE_STEP_SHRINKAGE_RATE;

			log("Likelihood difference = " + difference + ", Likelihood rate = "
			      + rate);
			log("Likelihood: " + currentLikelihood);
			log("RMSE: " + rmse);

			if(i % ITERATION_DUMP_COUNT == 0)
			{
				String index = String.valueOf(i);

				saveMatrix("beta", index, this.beta);
				saveMatrix("theta", index, this.theta);
				saveMatrix("v", index, this.v);
				saveTopWords(index);
			}
		}

		printMatricesInfo();

		saveMatrix("beta", "final", this.beta);
		saveMatrix("theta", "final", this.theta);
		saveMatrix("v", "final", this.v);
		saveTopWords("final");
		log("Model completed.");
	}

	public static void run() throws IOException, JSONException
	{
		log("Starting model...");

		List<List<WordInfo>> trainingDocuments = DatasetPreparer
		      .getWordCountsByBill("trainingPlsaDataset.txt");
		List<List<WordInfo>> crossValidationDocuments = DatasetPreparer
		      .getWordCountsByBill("crossValidationPlsaDataset.txt");

		Map<String, Integer> vocabulary = DatasetPreparer.getVocabularyDataset();

		List<Double> trainingControversyScores = DatasetPreparer
		      .getControversyScores("trainingDataset.txt");
		List<Double> crossValidationControversyScores = DatasetPreparer
		      .getControversyScores("crossValidationDataset.txt");

		log("Loaded datasets");

		for (int k : KS)
		{
			for (double eta : ETAS)
			{
				for (double lambda : LAMBDAS)
				{
					Model trainingModel = new Model(trainingDocuments, vocabulary,
					      trainingControversyScores, lambda, k, eta, null, null,
					      Purpose.TRAINING);

					Model crossValidationModel = new Model(crossValidationDocuments,
					      vocabulary, crossValidationControversyScores, lambda, k,
					      eta, trainingModel.beta, trainingModel.v,
					      Purpose.CROSS_VALIDATION);

					double rmse = crossValidationModel
					      .computeRmse(crossValidationControversyScores);

					log("Predicted RMSE: " + rmse);

					if(SINGLE_RUN)
						return;
				}
			}
		}
	}

	private void calculateTotalWordCount()
	{
		totalWordCount = 0;

		for (int d = 0; d < N; d++)
		{
			for (WordInfo wordInfo : documents.get(d))
			{
				totalWordCount += wordInfo.getCount();
			}
		}
	}

	private double averageNumberOfUniqueWordsPerDocument()
	{
		int sum = 0;

		for (List<WordInfo> document : documents)
			sum += document.size();

		return sum / documents.size();
	}

	private void printMatrix(String tag, Matrix matrix, int row, int column,
	      int rowLength, int columnLength)
	{
		log("Sample " + tag + " (" + matrix.getRowDimension() + ", "
		      + matrix.getColumnDimension() + "): ", false);

		for (int r = 0; r < rowLength; r++)
		{
			for (int c = 0; c < columnLength; c++)
			{
				System.out.print("[" + (row + r) + ", " + (column + c) + "] = "
				      + matrix.get(row + r, column + c) + " ");
			}
		}

		System.out.print('\n');
	}

	private Matrix toMatrix(List<Double> list)
	{
		double[] values = new double[list.size()];

		for (int i = 0; i < values.length; i++)
			values[i] = list.get(i);

		return new Matrix(values, values.length);
	}

	private void clearCache()
	{
		Arrays.fill(e_k, 0);

		for (int i = 0; i < e_kw.length; i++)
			Arrays.fill(e_kw[i], 0);

		for (int i = 0; i < e_dk.length; i++)
			Arrays.fill(e_dk[i], 0);
	}

	private void optimizeV()
	{
		switch(modelType)
		{
			case LINEAR:
				optimizeVLinearRegression();
				break;
			
			case QUADRATIC:
				optimizeVQuadraticRegression();
				break;
				
			case SIGMOID:
				optimizeVGradientAscent();
				break;
		}
	}
	
	private void optimizeVLinearRegression()
	{
		log("Begin optimizing v with linear regression:");
	
		double ridge = 1 / (K * Math.pow(eta, 2));

		LinearRegression linearRegression = new LinearRegression(thetaWithBias,
		      c, ridge);
		double[] coefficients = linearRegression.getCoefficients();

		v = new Matrix(coefficients, coefficients.length);
		log("Finished optimizing v with linear regression");		
	}
	
	private void optimizeVQuadraticRegression()
	{
		log("Begin optimizing v with quadratic regression:");
	
		double ridge = 1 / (K * Math.pow(eta, 2));

		Matrix thetaWithBiasSquared = thetaWithBiasSquared();
		
		LinearRegression linearRegression = new LinearRegression(thetaWithBiasSquared,
		      c, ridge);
		double[] coefficients = linearRegression.getCoefficients();

		v = new Matrix(coefficients, coefficients.length);
		log("Finished optimizing v with quadratic regression");		
	}
	
	
	private void optimizeVGradientAscent()
	{
		log("Begin optimizing V with gradient ascent: ");
		
		if (v == null)
			v = new Matrix(K + 1, 1, 0.5);

		double totalPartialDerivative = 0;
		double totalPartialDerivativeMagnitude = 0;
		double maxPartialDerivative = Double.MIN_VALUE;
		double minPartialDerivative = Double.MAX_VALUE;
		Matrix newV = new Matrix(K + 1, 1);

		for (int i = 0; i < MAX_V_ITERATIONS; i++)
		{
			for (int k = 0; k < K; k++)
			{
				double partialDerivative = partialDerivativeLikelihoodWrtV(k);
				double vk = v.get(k, 0) +  10 * partialDerivative;

				newV.set(k, 0, vk);
				
				if(partialDerivative > maxPartialDerivative)
					maxPartialDerivative = partialDerivative;

				if(partialDerivative < minPartialDerivative)
					minPartialDerivative = partialDerivative;

				totalPartialDerivative += partialDerivative;
				totalPartialDerivativeMagnitude += Math.abs(partialDerivative);				
			}
			
			v = newV;
		}
		
		log("V: Computed partial derivative count: " + K * MAX_V_ITERATIONS);
		log("V: Average partial derivative: " + totalPartialDerivative
		      / (K * MAX_V_ITERATIONS));
		log("V: Average partial derivative magnitude: "
		      + totalPartialDerivativeMagnitude / (K * MAX_V_ITERATIONS));
		log("V: Max partial derivative: " + maxPartialDerivative
		      + ", Min partial derivative: " + minPartialDerivative);
		log("V: Next step size = " + step);
		
		log("Finished optimizing V with gradient ascent");
	}
	
	private Matrix thetaWithBiasSquared()
	{
		Matrix thetaWithBiasSquared = new Matrix(thetaWithBias.getRowDimension(), thetaWithBias.getColumnDimension());
		
		for(int d = 0; d < thetaWithBias.getRowDimension(); d++)
		{
			for(int k = 0; k < thetaWithBias.getColumnDimension(); k++)
			{
				double theta_dk = thetaWithBias.get(d, k);
				
				thetaWithBiasSquared.set(d, k, theta_dk * theta_dk);
			}
		}
		
		return thetaWithBiasSquared;
	}
	
	
	private double partialDerivativeLikelihoodWrtV(int k)
	{
		double sum = 0;
		
		for(int d = 0; d < N; d++)
		{
			double c_d = c.get(d, 0);
			double delta = sigmoid(cachedThetaTimesV(d));
			double theta_dk = theta.get(d, k); 
			
			sum += (c_d - delta) * delta * (1 - delta) * theta_dk;
		}
		
		double v_k = v.get(k, 0);
		
		return lambda * (1/ N * sum - K/(eta * eta) * v_k);
	}

	private void optimizeBeta()
	{
		eStep();

		mStep();

		postEMStep();

		log("Finished optimizing beta");
	}

	private void eStep()
	{
		log("Evaluating e-step: ", false);

		for (int k = 0; k < K; k++)
		{

			System.out.print('.');
			System.out.flush();

			for (int d = 0; d < N; d++)
			{
				List<WordInfo> document = this.documents.get(d);

				for (WordInfo wordInfo : document)
				{
					int w = wordInfo.getId();

					double p = (theta.get(d, k) * beta.get(k, w) + SMOOTH_VALUE)
					      / computeSumThetaTimesBetaPerDocumentAndWord(d, w);

					double e = wordInfo.getCount() * p;

					e_kw[k][w] += e;

					e_k[k] += e;
				}
			}
		}

		System.out.print("\n");
		log("Finished e-step");
	}

	private double computeSumThetaTimesBetaPerDocumentAndWord(int d, int w)
	{
		double sum = 0d;

		for (int k = 0; k < K; k++)
			sum += theta.get(d, k) * beta.get(k, w);

		sum += K * SMOOTH_VALUE;
		return sum;
	}

	private void mStep()
	{
		log("Evaluating m-step: ", false);

		for (int k = 0; k < K; k++)
		{
			System.out.print('.');
			System.out.flush();

			for (int w = 0; w < W; w++)
			{
				double bkw = e_kw[k][w] / (e_k[k] + SMOOTH_VALUE) + SMOOTH_VALUE;

				beta.set(k, w, bkw);
			}
		}

		System.out.print('\n');
		log("Finished m-step");
	}

	private void postEMStep()
	{
		log("Evaluating post E-M step: ", false);

		for (int k = 0; k < K; k++)
		{

			System.out.print('.');
			System.out.flush();

			for (int d = 0; d < N; d++)
			{
				List<WordInfo> document = this.documents.get(d);

				for (WordInfo wordInfo : document)
				{

					int w = wordInfo.getId();

					double p = (theta.get(d, k) * beta.get(k, w) + SMOOTH_VALUE)
					      / computeSumThetaTimesBetaPerDocumentAndWord(d, w);

					double e = wordInfo.getCount() * p;

					e_dk[d][k] += e;
				}
			}
		}

		System.out.print("\n");
		log("Finished post E-M step");
	}

	private void optimizeTheta()
	{
		log("Optimizing theta: ", false);

		double totalPartialDerivative = 0;
		double totalPartialDerivativeMagnitude = 0;
		double maxPartialDerivative = Double.MIN_VALUE;
		double minPartialDerivative = Double.MAX_VALUE;

		for (int i = 0; i < MAX_THETA_ITERATIONS; i++)
		{
			Arrays.fill(thetaTimesVCache, null);
			Arrays.fill(thetaTimesVSquaredCache, null);

			System.out.print('.');
			System.out.flush();

			for (int k = 0; k < K; k++)
			{

				for (int d = 0; d < N; d++)
				{

					double partialDerivative = partialDerivativeLikelihoodWrtMu(d, k);

					double mu_dk = mu.get(d, k) + step * partialDerivative;

					mu.set(d, k, mu_dk);

					if(partialDerivative > maxPartialDerivative)
						maxPartialDerivative = partialDerivative;

					if(partialDerivative < minPartialDerivative)
						minPartialDerivative = partialDerivative;

					totalPartialDerivative += partialDerivative;
					totalPartialDerivativeMagnitude += Math.abs(partialDerivative);
				}
			}

			updateTheta();
		}

		System.out.print('\n');
		log("Theta: Computed partial derivative count: " + K * N * MAX_THETA_ITERATIONS);
		log("Theta: Average partial derivative: " + totalPartialDerivative
		      / (K * N * MAX_THETA_ITERATIONS));
		log("Theta: Average partial derivative magnitude: "
		      + totalPartialDerivativeMagnitude / (K * N * MAX_THETA_ITERATIONS));
		log("Theta: Max partial derivative: " + maxPartialDerivative
		      + ", Min partial derivative: " + minPartialDerivative);
		log("Theta: Next step size = " + step);
		log("Finished theta");
	}

	private double partialDerivativeLikelihoodWrtMu(int d, int k)
	{
		double sum = 0;

		for (int kp = 0; kp < K; kp++)
		{
			double product = 0;
			
			switch(modelType)
			{
				case LINEAR:
					product = partialDerivativeLikelihoodWrtThetaLinearModel(d, kp)
			      	* partialDerivativeThetaWrtMu(d, k, kp);
					break;

				case QUADRATIC:
					product = partialDerivativeLikelihoodWrtThetaQuadraticModel(d, kp)
		      		* partialDerivativeThetaWrtMu(d, k, kp);
					break;
					
				case SIGMOID:
					product = partialDerivativeLikelihoodWrtThetaSigmoidModel(d, kp)
				      * partialDerivativeThetaWrtMu(d, k, kp);
			}
			
			sum += product;
		}

		return sum;
	}

	private double partialDerivativeLikelihoodWrtThetaLinearModel(int d, int k)
	{

		double firstTerm = ((1 - lambda) / totalWordCount) * e_dk[d][k]
		      / (theta.get(d, k) + SMOOTH_VALUE);
		
		double secondTerm = 0;
		
		secondTerm = lambda / N * (cachedThetaTimesV(d) - c.get(d, 0))  * v.get(k, 0);
		return firstTerm - secondTerm;
	}
	
	private double partialDerivativeLikelihoodWrtThetaQuadraticModel(int d, int k)
	{

		double firstTerm = ((1 - lambda) / totalWordCount) * e_dk[d][k]
		      / (theta.get(d, k) + SMOOTH_VALUE);
		
		double secondTerm = 0;
		
		if(purpose == Purpose.TRAINING)
			secondTerm = lambda / N * 2 * (cachedThetaTimesVSquared(d) - c.get(d, 0))  * theta.get(d, k) * v.get(k, 0);
		
		return firstTerm - secondTerm;
	}
	
	private double partialDerivativeLikelihoodWrtThetaSigmoidModel(int d, int k)
	{
		double firstTerm = ((1 - lambda) / totalWordCount) * e_dk[d][k]
		      / (theta.get(d, k) + SMOOTH_VALUE);
		double delta = sigmoid(cachedThetaTimesV(d));	
		double cd = c.get(d, 0);
		double secondTerm = 0;
		
		secondTerm = lambda / N * (cd - delta) * delta * (1 - cd) * v.get(k, 0);
		return firstTerm - secondTerm;
	}

	private double partialDerivativeThetaWrtMu(int d, int k, int kp)
	{

		if(k == kp)
			return theta.get(d, k) * (1 - theta.get(d, k));
		else
			return -1 * theta.get(d, kp) * theta.get(d, k);
	}

	private double cachedThetaTimesV(int d)
	{
		Double value = thetaTimesVCache[d];

		if(value != null)
			return value;

		value = thetaTimesV(d);
		return value;
	}
	
	private double cachedThetaTimesVSquared(int d)
	{
		Double value = thetaTimesVSquaredCache[d];

		if(value != null)
			return value;

		value = 0d;
		
		for(int k = 0; k < K + 1; k++)
		{
			double theta_dk = thetaWithBias.get(d, k);
			
			value += theta_dk * theta_dk * v.get(k, 0);
		}
		
		return value;
	}
	
	private double thetaTimesV(int d)
	{
		return thetaTimesV(thetaWithBias, d);
	}
	
	private double thetaTimesV(Matrix thetaWithBias, int d)
	{
		double value = 0d;
		
		for (int k = 0; k < K + 1; k++)
			value += thetaWithBias.get(d, k) * v.get(k, 0);
		
		return value;		
	}

	private double likelihood() throws IOException
	{
		double result;
		double tcf = topicLikelihood();;
		
		if(purpose == Purpose.TRAINING)
		{
			double cscf = controversyLikelihood();
			
			result = (1 - lambda) * tcf - lambda * cscf;
			log("Topic likelihood = " + tcf + ", Controversy score likelihood = "
			      + cscf);
			log("Product 1 = " + (1 - lambda) * tcf + ", Product 2 = " + lambda
			      * cscf + ", LAMBDA = " + lambda);			
		}
		else
		{
			log("Topic likelihood = " + tcf + ", LAMBDA = " + lambda);
			result = tcf;
		}
		
		perplexity = Math.exp(-1 * tcf);
		log("Perplexity: " + perplexity);
		return result;
	}

	private double topicLikelihood()
	{
		double result = 0d;

		for (int d = 0; d < N; d++)
		{
			List<WordInfo> document = documents.get(d);

			for (WordInfo wordInfo : document)
			{
				int w = wordInfo.getId();
				double sum = 0d;

				for (int k = 0; k < K; k++)
					sum += theta.get(d, k) * beta.get(k, w);

				assert (sum != 0);

				result += wordInfo.getCount() * Math.log(sum);
			}
		}

		return result / totalWordCount;
	}

	private double controversyLikelihood()
	{
		double totalSquareError = computeTotalSquareError();

		return totalSquareError / (2 * N) + regularization();
	}
	
	private double computeTotalSquareError()
	{
		return computeTotalSquareError(c);
	}

	private double computeTotalSquareError(Matrix c)
	{
		double totalSquareError = 0d;

		for (int d = 0; d < N; d++)
		{
			double prediction = 0;
			
			switch(modelType)
			{
				case LINEAR:
					prediction = thetaTimesV(d);
					break;
					
				case QUADRATIC:
					prediction = thetaTimesV(thetaWithBiasSquared(), d);
					break;
					
				case SIGMOID:
					prediction = sigmoid(thetaTimesV(d));
					break;
			}
			
			totalSquareError += Math.pow(c.get(d, 0) - prediction, 2);
		}

		return totalSquareError;
	}

	private double regularization()
	{
		double squareTotal = 0d;

		for (int k = 0; k < K; k++)
			squareTotal += Math.pow(v.get(k, 0), 2);

		return 1 / (2 * K * Math.pow(eta, 2)) * squareTotal;
	}

	private static void log(String message)
	{
		log(message, true);
	}

	private static void log(String message, boolean newLine)
	{
		System.out.print("[" + new Date() + "]: " + message
		      + (newLine ? '\n' : ""));
		System.out.flush();
	}

	private void gaussianRandomizeMatrix(Matrix matrix)
	{
		for (int r = 0; r < matrix.getRowDimension(); r++)
		{
			for (int c = 0; c < matrix.getColumnDimension(); c++)
			{
				Random random = new Random();
				double number = random.nextGaussian();

				matrix.set(r, c, number);
			}
		}
	}

	private void initializeStochasticMatrix(Matrix matrix)
	{
		for (int r = 0; r < matrix.getRowDimension(); r++)
		{
			double sum = 0d;

			for (int c = 0; c < matrix.getColumnDimension(); c++)
			{
				double value = Math.random() + 1;

				matrix.set(r, c, value);
				sum += value;
			}

			for (int c = 0; c < matrix.getColumnDimension(); c++)
			{
				double value = matrix.get(r, c) / sum;

				matrix.set(r, c, value);
			}
		}
	}

	private void updateTheta()
	{
		for (int d = 0; d < N; d++)
		{
			for (int k = 0; k < K; k++)
			{
				double value = muToTheta(d, k);

				theta.set(d, k, value);
			}

			double temp1 = theta.get(d, K - 1);
			double temp2 = theta.get(d, lastMuColumns[d]);

			theta.set(d, K - 1, temp2);
			theta.set(d, lastMuColumns[d], temp1);
		}

		thetaWithBias.setMatrix(0, N - 1, 0, K - 1, theta);
	}

	private double muToTheta(int d, int k)
	{
		double sum = 0d;
		double numerator = (k == K - 1 ? 1 : Math.exp(mu.get(d, k)));

		for (int kp = 0; kp < K - 1; kp++)
			sum += Math.exp(mu.get(d, kp));

		return numerator / (1 + sum);
	}

	private double computeRmse(Matrix ca)
	{
		double totalSquareError = computeTotalSquareError(ca);

		return Math.sqrt(totalSquareError / c.getRowDimension());
	}

	private double computeRmse()
	{
		return computeRmse(c);
	}

	private double computeRmse(List<Double> actualControversyScores)
	      throws IOException
	{
		saveMatrix("predicted_controversy_scores", null, c);

		double rmse = computeRmse(toMatrix(actualControversyScores));

		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename("predicted_rmse"), String.valueOf(rmse) + "\n");
		return rmse;
	}

	private void initializeLastMuColumns()
	{
		for (int i = 0; i < N; i++)
			lastMuColumns[i] = (int) (Math.random() * K);
	}

	private void printMatricesInfo()
	{

		if(this.v != null)
			printMatrix("V", this.v, 0, 0, 10, 1);

		printMatrix("Theta", this.theta, 0, 0, 5, 5);
		printMatrix("Mu", this.mu, 0, 0, 5, 5);
		printMatrix("Beta", this.beta, 0, 0, 5, 5);

		printStochasticMatrixInfo("theta", this.theta);
		printStochasticMatrixInfo("beta", this.beta);
	}

	private void printStochasticMatrixInfo(String tag, Matrix matrix)
	{
		int invalidCount = 0;
		double totalSum = 0d;
		double minValue = 1;
		double maxValue = 0;

		for (int r = 0; r < matrix.getRowDimension(); r++)
		{
			double sum = 0d;

			for (int c = 0; c < matrix.getColumnDimension(); c++)
			{
				double p = matrix.get(r, c);

				if(p > maxValue)
					maxValue = p;

				if(p < minValue)
					minValue = p;

				sum += p;
			}

			if(Math.abs(1 - sum) > EPSILON)
				invalidCount++;

			totalSum += sum;
		}

		double averageSum = totalSum / matrix.getRowDimension();

		log("Stochastic matrix info for " + tag + ": Invalid count = "
		      + invalidCount + ", " + "Average sum = " + averageSum + ", "
		      + "Max: " + maxValue + ", Min: " + minValue);
	}

	private void assertStochasticMatrix(Matrix matrix)
	{
		for (int r = 0; r < matrix.getRowDimension(); r++)
		{
			double sum = 0d;

			for (int c = 0; c < matrix.getColumnDimension(); c++)
			{
				if(-EPSILON > matrix.get(r, c) || matrix.get(r, c) > 1 + EPSILON)
				{
					throw new IllegalStateException("matrix[" + r + ", " + c + "]"
					      + matrix.get(r, c));
				}

				sum += matrix.get(r, c);
			}

			if(Math.abs(1 - sum) > EPSILON)
				throw new IllegalStateException("Sum = " + sum + " not in [0, 1]");
		}
	}
	
	private List<Pair<Integer, Double>> sortStochasticMatrixRow(
	      Matrix matrix, int row)
	{
		Map<Integer, Double> rowMap = new HashMap<Integer, Double>();

		for (int c = 0; c < matrix.getColumnDimension(); c++)
			rowMap.put(c, matrix.get(row, c));

		return Utility.sortByValue(rowMap);
	}
	
	private  List<Pair<String, Double>> getTopWordsByTopic(String[] words,
	      Matrix beta, int k, int topCount)
	{
		List<Pair<String, Double>> topWords = new ArrayList<Pair<String, Double>>();
		List<Pair<Integer, Double>> sortedWords = sortStochasticMatrixRow(beta, k);

		for (int i = 0; i < topCount; i++)
		{
			Pair<Integer, Double> pair = sortedWords.get(i);
			int wordId = pair.getFirst();
			Double probability = pair.getSecond();

			topWords.add(new Pair<String, Double>(words[wordId], probability));
		}

		return topWords;
	}

	private void saveTopWords(String suffix) throws IOException
	{
		StringBuilder builder = new StringBuilder(1024);

		for (int k = 0; k < K; k++)
		{
			List<Pair<String, Double>> topWords = 
					getTopWordsByTopic(words, beta, k, 40);

			builder.append("Topic ");
			builder.append(k);

			if(v != null)
			{
				builder.append(" (V = ");
				builder.append(v.get(k, 0));
				builder.append(')');
			}

			builder.append(".\n");

			for (Pair<String, Double> pair : topWords)
			{
				builder.append(pair.getFirst());
				builder.append(" (");
				builder.append(pair.getSecond());
				builder.append(")\n");
			}

			builder.append('\n');
		}

		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename("top_words", suffix), builder.toString());
	}

	private void initializeProgressFiles() throws IOException
	{

		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY, getOutputFilename("rmse"),
		      "\n");
		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename("likelihood"), "\n");
		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename("perplexity"), "\n");
	}

	private void saveProgress(double rmse, double likelihood) throws IOException
	{
		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY, getOutputFilename("rmse"),
		      String.valueOf(rmse) + "\n", true);
		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename("likelihood"), String.valueOf(likelihood) + "\n",
		      true);
		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename("perplexity"), String.valueOf(perplexity) + "\n",
		      true);
	}

	private void saveMatrix(String name, String suffix, Matrix matrix)
	      throws IOException
	{
		Utility.saveData(MODEL_PERSISTENCE_DIRECTORY,
		      getOutputFilename(name, suffix), matrix);
	}

	private String getOutputFilename(String prefix)
	{
		return getOutputFilename(prefix, null);
	}
	
	public String getOutputFilename(String prefix, String suffix,
	      String purpose, double lambda, int k, double eta)
	{
		String name = prefix + "_" + purpose;
		DecimalFormat formater = new DecimalFormat("0.###");

		name += "_l" + formater.format(lambda);
		name += "_e" + (eta < 1d ? formater.format(eta) : (int) eta);
		name += "_k" + k;

		if(suffix != null)
			name += "_" + suffix;

		name += ".txt";
		return name;
	}

	private String getOutputFilename(String prefix, String suffix)
	{
		String purposeText = null;

		switch (purpose)
		{
			case TRAINING:
				purposeText = "train";
				break;

			case CROSS_VALIDATION:
				purposeText = "cv";
				break;

			case TESTING:
			   purposeText = "test";
				break;
		}

		return getOutputFilename(prefix, suffix, purposeText, lambda, K, eta);
	}
	
	private double sigmoid(double value)
	{
		return 1 / (1 + Math.exp(-1 * value));
	}
}
