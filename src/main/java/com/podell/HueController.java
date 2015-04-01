package main.java.com.podell;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

import nl.q42.jue.FullLight;
import nl.q42.jue.HueBridge;
import nl.q42.jue.Light;
import nl.q42.jue.State.AlertMode;
import nl.q42.jue.StateUpdate;
import nl.q42.jue.exceptions.ApiException;

import org.apache.http.client.ClientProtocolException;
import org.apache.commons.io.IOUtils;

/**
 * A small daemon to update hue colors based on Jenkins status
 * https://github.com/Q42/Jue/wiki
 */
public class HueController {   
    private static Properties prop = null;
    private static HueBridge bridge = null;
	private static ArrayList<Lamp> arrayLamps = new ArrayList<Lamp>();   
	     
	// An object standing in for a structure, contains the info for a individual hue lamp
    public class Lamp {
    	String name;
    	Light light;
    	FullLight fullLight;
    	StateUpdate su;
    	String jenkinsColor;
    	String jenkinsJob;
    }
    
    // The comma delimited values from jenkins_blue_hue_sat_bright_alert
    public enum EnumHue {
    	HUE, SATURATION, BRIGHTNESS, ALERT
     }

    // Constructor
    // To discover the IP of the Hue bridge on your local network, navigate to  https://www.meethue.com/api/nupnp
    public HueController() {
   		boolean hasConnectedToTheBridge = false;
   		
		while( hasConnectedToTheBridge == false ) {
			try {
     			
	    		if( ! getPropValues() )
	    			return;
	          	bridge = new HueBridge((String)prop.getProperty("hueBridgeIP"));
	          	bridge.authenticate("ffdcJenkins");
		    	String newUser = bridge.getUsername();
		    	
		    	System.out.println("Connected to the bridge as '" + newUser + "'");
		    	
	 	    	// Initialize lamp array
		    	int i = 1;
				for (Light light : bridge.getLights()) {
					String job = (String)prop.getProperty("job"+ i++);
				    if( job == null )
				    	break;
				    Lamp lamp = new Lamp();
				    lamp.jenkinsJob = job;
					lamp.light = light;
				    lamp.fullLight = bridge.getLight(light);
				    lamp.name = lamp.fullLight.getName();
				    System.out.println(lamp.name + ", brightness: " + lamp.fullLight.getState().getBrightness() + ", hue: " + lamp.fullLight.getState().getHue() 
				    		+ ", saturation: " + lamp.fullLight.getState().getSaturation() + ", temperature: " + lamp.fullLight.getState().getColorTemperature());
				    arrayLamps.add(lamp);
				}				
	    	} catch (IOException e) {
	    		System.out.println("IoException 1 " + e );
			} catch (ApiException e) {
	    		System.out.println("ApiException 1 " + e );
			} catch (NullPointerException e) {
		  		System.out.println("NullPointerException 1 " + e );
			}     	

			hasConnectedToTheBridge = true;
 		}
    }

    // https://ci.dev.financialforce.com/api/xml
    // https://ci.dev.financialforce.com/view/PSA/api/json
    static boolean jenkinsStateUpdater( boolean isDebugLogging ) {
    	try {
			String json = getJenkinsDomCurl((String)prop.getProperty("urlString"), (String)prop.getProperty("username"), (String)prop.getProperty("password"));
			StringBuilder sb = new StringBuilder();
			for(Lamp lamp : arrayLamps) {
				lamp.jenkinsColor = getColor(json, lamp.jenkinsJob);
				if( lamp.jenkinsColor == null ) {
						System.out.println("Unable to match a current Jenkins job for lamp '" + lamp.name + "' and job name '" + lamp.jenkinsJob + "'");
						continue;
				}
				lamp.su = buildStateUpdateForAJenkinsColor(lamp.jenkinsColor);
				bridge.setLightState(lamp.light, lamp.su);
				sb.append("'" +lamp.name + "' -> '" + lamp.jenkinsColor + "' (" + lamp.fullLight.getState().getBrightness() + ") for '" + 
							lamp.jenkinsJob + "';  ");
			}
			
			if (isDebugLogging)
				System.out.println( new SimpleDateFormat("MM/dd/yyyy HH:mm:ss - ").format(Calendar.getInstance().getTime()) + sb );
		} catch (ClientProtocolException e) {
	  		System.out.println("ClientProtocolException " + e );
		} catch (IOException e) {
	  		System.out.println("IoException 2 " + e );
		} catch (ApiException e) {
	  		System.out.println("ApiException 2 " + e );
		} catch (NullPointerException e) {
	  		System.out.println("NullPointerException 2 " + e );
		}
        
        return true;
    }
    
    static StateUpdate buildStateUpdateForAJenkinsColor( String jenkinsColor ) {
    	String propKey = null;
    	
    	if(jenkinsColor.equals("blue")) {
    		propKey = "jenkins_blue_hue_sat_bright_alert";
    	} else if (jenkinsColor.equals("red")) {
    		propKey = "jenkins_red_hue_sat_bright_alert";
    	} else if (jenkinsColor.equals("red")) {
    		propKey = "jenkins_blue_hue_sat_bright_alert";
    	} else if (jenkinsColor.equals("blue_anime")) {
    		propKey = "jenkins_blue_anime_hue_sat_bright_alert";
    	} else if (jenkinsColor.equals("red_anime")) {
    		propKey = "jenkins_red_anime_hue_sat_bright_alert";
    	} else {
    		// "notbuilt" or "disabled", is dim white
    		propKey = "jenkins_disabled_hue_sat_bright_alert";
    	}
    	
    	if( propKey != null ) {
    		try {
	           	List<String> hueComponents = new ArrayList<String>(Arrays.asList(prop.getProperty(propKey).split(",")));
	
	     		StateUpdate su = new StateUpdate().turnOn().setHue(Integer.valueOf(hueComponents.get(EnumHue.HUE.ordinal())))
	     				.setBrightness(Integer.valueOf(hueComponents.get(EnumHue.BRIGHTNESS.ordinal())))
	     				.setSat(Integer.valueOf(hueComponents.get(EnumHue.SATURATION.ordinal())));
	     	    if( hueComponents.get(EnumHue.ALERT.ordinal()).compareToIgnoreCase("true" ) == 0 )
	     	    	return su.setAlert(AlertMode.SELECT);
	     	    else 
	     	    	return  su.setAlert(AlertMode.NONE);
    		} catch (Exception e) {
    			System.out.println("The config.properties value for '" + propKey + "' did not parse.  " + e);
    		}
    	}
		return null;
    }
    	
    	
    /**
     * Another hack that avoids doing a dom parsing, just to find the color of the job
     * @param json
     * @param jobName
     * @return String, the color or null if not found
     */
    static String getColor(String json, String jobName) {
    	int nameIndex = json.indexOf(jobName);
    	if(nameIndex > 0) {
    		int colorIndex = json.indexOf("\"color\":\"", nameIndex) + 9; {
    			int endColorIndex = json.indexOf('"', colorIndex); {
    				if(endColorIndex > 0) {
    					String color = json.substring(colorIndex, endColorIndex);
    					//System.out.println("Color is: " + color);
    					return color;
    				}		
    			}
    		}
    	}
    	return null;
    }
     
	/**
	 * A hack to avoid spending hours on httpclient setup for a secure jenkins site
	 * $ curl -u spodell%40financialforce.com:myapitokene1eb6eb5566fe3173914444802b44056 https://ci.dev.financialforce.com/api/xml
	 * https://ci.dev.financialforce.com/view/PSA/api/json
	 * @param urlString
	 * @param username
	 * @param password
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static String getJenkinsDomCurl(String urlString, String username, String password) throws ClientProtocolException, IOException {
		String token = username + ":" + password;
	
	    ProcessBuilder pb = new ProcessBuilder(
	            "curl",
	            "-u",
	            token,
	            urlString);
	
	    // pb.redirectErrorStream(true);
	    Process p = pb.start(); 
	    String out = IOUtils.toString(p.getInputStream(), "UTF-8");
	    // System.out.println(out);
	    return out;
	}

	public static boolean getPropValues() throws IOException {
		prop = new Properties();
		
		InputStream in = HueController.class.getResourceAsStream("config.properties");
		if(in == null) {
			System.out.println("The config.properties file is not on the classpath!");
			return false;
		}
		prop.load(in);
		return true;
	}
    
    /**
     * Main  Hue Controller
     * https://github.com/SailingSteve/hue3.git
	 * Steve Podell
	 * @param args, "debug" to enable the status update log for each update cycle
	 */
    public static void main(String[] args) {
    	boolean isDebugLogging = Arrays.asList(args).contains("debug"); 
  
    	new HueController();
		if (prop == null) {
	  		System.out.println( "unable to find config.properites file on the classpath: " + System.getProperty("java.class.path"));
		} 
		else while( true ) {
	  		jenkinsStateUpdater(isDebugLogging); 
	  		try {
	  			Integer delay = new Integer(prop.getProperty("polling_delay_ms"));
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				return;
			} catch (NumberFormatException nfe) {
		  		System.out.println( "Unable to parse : '" + prop.getProperty("polling_delay_ms") + "' as an integer number of milliseconds");
			}
	  	}
    }  
}
