package app.babelfish;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import java.util.*;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

//import com.amazonaws.regions.Regions;
import com.amazonaws.services.lexruntime.AmazonLexRuntime;
import com.amazonaws.services.lexruntime.AmazonLexRuntimeClientBuilder;
import com.amazonaws.services.lexruntime.model.PostTextRequest;
import com.amazonaws.services.lexruntime.model.PostTextResult;
import com.amazonaws.services.lexmodelbuilding.model.GetUtterancesViewResult;
import com.amazonaws.services.lexmodelbuilding.model.UtteranceList;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.amazonaws.services.polly.model.VoiceId;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.translate.AmazonTranslateClient;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;
import com.amazonaws.transcribestreaming.TranscribeStreamingClientWrapper;
import com.amazonaws.transcribestreaming.TranscribeStreamingSynchronousClient;

import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;


public class LambdaHandler implements RequestHandler<Input, String> {
	
	AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	AmazonTranslate translate = AmazonTranslateClient.builder().build();
	AmazonPolly polly = AmazonPollyClientBuilder.defaultClient();
	AmazonLexRuntime lexclient = AmazonLexRuntimeClientBuilder.standard().withRegion("us-east-1").build();
	PostTextRequest textRequest = new PostTextRequest();
	PostTextRequest textRequests = new PostTextRequest();
	GetUtterancesViewResult utteranceObj = new GetUtterancesViewResult();
	List<UtteranceList> utterlist = new ArrayList<UtteranceList>();


    
    
	@Override
	public String handleRequest(Input name, Context context) {
		
		LambdaLogger logger = context.getLogger();
		
		logger.log("Bucket: " + name.getBucket());
		logger.log("Key: " + name.getKey());
		logger.log("Source Language: " + "ca");
		logger.log("Target: " + "ca");
		utteranceObj.setBotName("UtterBot");
		textRequests.setBotName("UtterBot");
		textRequests.setBotAlias("Text");
		textRequests.setUserId("testuser");
		textRequest.setBotName("CEZ");
		textRequest.setBotAlias("VCEZLex");
		textRequest.setUserId("testuser");
		
	 String frenchText ="Bonjour, comment allez-vous";
		textRequests.setInputText(frenchText);
		PostTextResult textResults = lexclient.postText(textRequests);
		utterlist = utteranceObj.getUtterances();
		logger.log(utterlist.toString());
	 //String firstInput = synthesize(logger, frenchText, "ca","/tmp/op.mp3");
	 //String fileNames = saveOnS3(name.getBucket(), firstInput,"input/op.mp3");
		//File inputFiles = new File("https://voicetranslatorapp-voicetranslatorbucket-1qugl31wlvtv2.s3.amazonaws.com/input/op.mp3");
	//S3Object fullObject = s3.getObject(new GetObjectRequest(name.getBucket(), "input/op.mp3"));
	//String fileNames = saveOnS3(name.getBucket(), inputFiles);
		//File inputFiles = new File("/tmp/op.mp3");
	// TranscribeStreamingSynchronousClient synchronousClient = new TranscribeStreamingSynchronousClient(TranscribeStreamingClientWrapper.getClient());
	// String transcripts = synchronousClient.transcribeFile(LanguageCode.FR_CA, inputFiles);
		//Converting Audio to Text using Amazon Transcribe service.
       String transcript = transcribe(logger, name.getBucket(), name.getKey(), "ca");

        //Translating text from one language to another using Amazon Translate service.
		String translatedText1 = translate(logger, transcript,"ca", "en");
		textRequest.setInputText(translatedText1);
		PostTextResult textResult = lexclient.postText(textRequest);

		String outlex = textResult.getMessage();
		String translatedText = translate(logger, outlex, "en", "ca");

        
        //Converting text to Audio using Amazon Polly service.
        String outputFile = synthesize(logger, translatedText, "ca", "/tmp/output.mp3");
        
        //Saving output file on S3.
        String fileName = saveOnS3(name.getBucket(), outputFile, "output/output.mp3");
    	
		return fileName;
	}
	
	private String saveOnS3(String bucket, String outputFile, String fileName) {
		
		//String fileName = "output/" + new Date().getTime() + ".mp3";
		
		PutObjectRequest request = new PutObjectRequest(bucket, fileName, new File(outputFile));
		request.setCannedAcl(CannedAccessControlList.PublicRead);
		s3.putObject(request);
		
		return fileName;
		
	}

	private String transcribe(LambdaLogger logger, String bucket, String key, String sourceLanguage) {
		
		LanguageCode languageCode = LanguageCode.FR_CA;
		
		if ( sourceLanguage.equals("es") ) {
			languageCode = LanguageCode.ES_US;
		}
		
		if ( sourceLanguage.equals("gb") ) {
			languageCode = LanguageCode.EN_GB;
		}
		
		if ( sourceLanguage.equals("ca") ) {
			languageCode = LanguageCode.FR_CA;
		}
		
		if ( sourceLanguage.equals("fr") ) {
			languageCode = LanguageCode.FR_FR;
		}
		
		
		File inputFile = new File("/tmp/input.wav");
		
    	s3.getObject(new GetObjectRequest(bucket, key), inputFile);

       TranscribeStreamingSynchronousClient synchronousClient = new TranscribeStreamingSynchronousClient(TranscribeStreamingClientWrapper.getClient());
        String transcript = synchronousClient.transcribeFile(languageCode, inputFile);
     
        logger.log("Transcript: " + transcript);
 
        return transcript;
	}
	
	private String translate(LambdaLogger logger, String text, String sourceLanguage, String targetLanguage) {
		
		if (targetLanguage.equals("ca")) {
			targetLanguage = "fr";
		}
		
		if (targetLanguage.equals("gb")) {
			targetLanguage = "en";
		}
		
		TranslateTextRequest request = new TranslateTextRequest().withText(text)
                .withSourceLanguageCode(sourceLanguage)
                .withTargetLanguageCode(targetLanguage);
        TranslateTextResult result  = translate.translateText(request);
        
        String translatedText = result.getTranslatedText();
        
        logger.log("Translation: " + translatedText);
        
        return translatedText;
		
	}
	
    private String synthesize(LambdaLogger logger, String text, String language, String outputFileName) {
    	
    	VoiceId voiceId = null;

    	if (language.equals("en") ) {
    		voiceId = VoiceId.Matthew;
    	} 
    	
    	if (language.equals("pl") ) {
    		voiceId = VoiceId.Maja;
    	} 
    	
    	if (language.equals("es") ) {
    		voiceId = VoiceId.Miguel;
    	} 
    	
    	if (language.equals("fr") ) {
    		voiceId = VoiceId.Mathieu;
    	} 
    	
    	if (language.equals("ja") ) {
    		voiceId = VoiceId.Takumi;
    	} 
    	
    	if (language.equals("ru") ) {
    		voiceId = VoiceId.Maxim;
    	} 
    	
    	if (language.equals("de") ) {
    		voiceId = VoiceId.Hans;
    	} 
    	
    	if (language.equals("it") ) {
    		voiceId = VoiceId.Giorgio;
    	} 
    	
    	if (language.equals("sv") ) {
    		voiceId = VoiceId.Astrid;
    	} 
    	
    	if (language.equals("gb") ) {
    		voiceId = VoiceId.Brian;
    	} 
    	
    	if (language.equals("ca") ) {
    		voiceId = VoiceId.Chantal;
    	} 

    	
        //String outputFileName = "/tmp/output.mp3";
 
        SynthesizeSpeechRequest synthesizeSpeechRequest = new SynthesizeSpeechRequest().withOutputFormat(OutputFormat.Mp3).withSampleRate("8000").withVoiceId(voiceId).withText(text);
 
        try (FileOutputStream outputStream = new FileOutputStream(new File(outputFileName))) {
            SynthesizeSpeechResult synthesizeSpeechResult = polly.synthesizeSpeech(synthesizeSpeechRequest);
            byte[] buffer = new byte[2 * 1024];
            int readBytes;
 
            try (InputStream in = synthesizeSpeechResult.getAudioStream()){
                while ((readBytes = in.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, readBytes);
                }
            }
            
        } catch (Exception e) {
        	logger.log(e.toString());
        }
        
        return outputFileName;
    }
}

class Input {
	private String bucket;
	private String key;
	private String sourceLanguage;
	private String targetLanguage;
	public String getBucket() {
		return bucket;
	}
	public void setBucket(String bucket) {
		this.bucket = bucket;
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getSourceLanguage() {
		return sourceLanguage;
	}
	public void setSourceLanguage(String sourceLanguage) {
		this.sourceLanguage = sourceLanguage;
	}
	public String getTargetLanguage() {
		return targetLanguage;
	}
	public void setTargetLanguage(String targetLanguage) {
		this.targetLanguage = targetLanguage;
	}
	
	
	
	
	
}
