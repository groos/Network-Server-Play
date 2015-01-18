import java.io.*; // import I/O libraries
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;


/*--------------------------------------------------------

1. Nick Groos / 5/10/2014: MyWebServer

2. Java build 1.7.0_25-b15

3. To compile, in command window type:
	> javac MyWebServer.java

4. To run the program:
	> java MyWebServer

	The server will run and wait for a connection from the client. 

5. List of files needed for running the program.

 a. MyWebServer.java

5. Notes:

	I developed this on a PC, and had everything working. Tonight I just
	happen to run it on my Mac, and everything was broken because of the
	different pathnames - back vs forward slash!!!
	
	I just hacked a bunch of fixes in for the pathname, and now as far as I
	can tell everything is back to normal for both PC and Mac/Linux native
	pathnames. 
	
	I did not consider security until late in this project,
	so there are no restrictions on what is displayed in directories in terms of
	file extensions, folders, etc. There is hard-coded protection against access
	above the server's root directory.

----------------------------------------------------------*/


class WebWorker extends Thread{ //subclass of Thread
	Socket sock; // create the socket, local for the Worker
	
	/*
	 * construct the worker, assign its datamember Socket
	 */
	WebWorker (Socket s) { 
		sock = s;
	}
	
	/*
	 * function to parse GET request for CGI
	 * 
	 * Add the numbers together, and send the MIME header
	 * along with result back to client.
	 * 
	 * 
	 *       WebForm Format:
	 *  GET /cgi/addnums.fake-cgi?person=Nick&num1=4&num2=5 HTTP/1.1
	 */
	public void addNums(String GETRequest, String httpVersion){
		PrintStream out = null;
		BufferedReader in = null;
		String name, result;
		Integer num1, num2, sum;
		
		/*
		 * get the param values. Could set up a data structure with key-value
		 * to do this programmatically. Just going to hard code for now...
		 */
		int start = GETRequest.indexOf('=') + 1;
		name = getParam(GETRequest, start);
		
		start = GETRequest.indexOf('=', start) + 1;
		num1 = Integer.valueOf(getParam(GETRequest, start));
		
		start = GETRequest.indexOf('=', start) + 1;
		num2 = Integer.valueOf(getParam(GETRequest, start));
		
		sum = num1 + num2;
		
		result = "<html><body><center>" +
			 "Dear " + name + ", the sum of " + num1 + " and " + num2 + " is " + sum + ".<br>" +
			 "<a href=\"/\">Parent Directory</a> <br> </center></body></html>";
		
		try {
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			out = new PrintStream(sock.getOutputStream());
			
			out.print(httpVersion + ' ' + 200 + " OK\n");
			out.print("Content-Length: " + result.length() + '\n');
			out.print("Content-Type: text/html\n\n");
			out.print(result);
			
		} catch (IOException ioe){
			ioe.printStackTrace();
		}
	}
	
	// returns the parameter 
	public String getParam(String request, int start){
		String param;
		/*
		 * A bit hackey, but gets each param delimited by &, until it fails on last one,
		 * where it grabs by ' '
		 */
		try{
			param = request.substring(start, request.indexOf('&', start));
		} catch (StringIndexOutOfBoundsException e){
			param = request.substring(start, request.indexOf(' ', start));
		}
		
		System.out.println("got param: " + param);
		
		return param;
	}

public void run(){
	// I/O streams for data going in/out of the socket.
	PrintStream out = null;
	BufferedReader in = null;
	
	/*
	 * initialize streams, try to make a reader from the socket's Input/Output Stream 
	 */
	try{
		in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		out = new PrintStream(sock.getOutputStream());
		
		// shut down the server if controlSwitch says so!
		if (MyWebServer.controlSwitch != true){
			System.out.println("Lister is now shutting down per client request.");
			out.println("Server is now shutting down. Bu-Bye!");
		}
		/*
		 * Get the http requests and parse them. the incoming request will look like this:
		 *      "GET /elliott/dog.txt HTTP/1.1\nHost: condor.depaul.edu:80\n\n";
		 *      
		 *      WebForm Format:
		 *      GET /cgi/addnums.fake-cgi?person=Nick&num1=4&num2=5 HTTP/1.1
		 */
		else try{
			
			// Lots of variable declarations for the parsing
			String GETRequest, hostLine;
			String host, port, httpVersion;
			String fileExtension = null;
			String filename = null;
			String ServerResponse = "";
			int hostEnd;
			int fileEnd = 0;
			int fileSize = 0;
			Path file = null;
			String contentType = null;
			
			GETRequest = in.readLine();
			hostLine = in.readLine();
			
			System.out.println(GETRequest);
			System.out.println(hostLine);
			
			httpVersion = GETRequest.substring(GETRequest.indexOf("HTTP"));

			hostEnd = hostLine.lastIndexOf(':');
			host = hostLine.substring(6, hostEnd);
			port = hostLine.substring(hostEnd + 1);
			
			// Parsing the HTTP request for components
			// Put in this null check because there were repeated connections 
			// resulting in null pointers 
			if (GETRequest != null){
				fileEnd = GETRequest.indexOf("HTTP") - 1;
			
				if (GETRequest.charAt(fileEnd - 1) == '/' && GETRequest.charAt(5) != ' '){
					fileEnd = fileEnd - 1; 
				}
				
				// check for cgi
				if(GETRequest.toLowerCase().indexOf(".fake-cgi?") >= 0){
					
					// addNums will parse request and send result back to client.
					addNums(GETRequest, httpVersion);
					
				}
				else{
					// if it's not a cgi, then treat it like a normal file.
					filename = (GETRequest.charAt(0) != ' ') ? GETRequest.substring(5, fileEnd) : "";
					fileExtension = filename.substring(filename.indexOf('.') + 1);
				}
			}
			
			// fileExtension = filename.substring(filename.indexOf('.') + 1);
			
			
			/*
			 * if we have an extension, then set up the file path to send the data
			 */
			if (fileExtension != null && (fileExtension.equals("txt") 
					|| fileExtension.equals("html") 
					|| fileExtension.equals("ico"))){
				
				
				String workingDir = System.getProperty("user.dir");
				if (workingDir.indexOf('\\') >= 0){
					filename = filename.replace('/', '\\');
				}
				else if (workingDir.indexOf('/') >= 0){
					if (filename.indexOf('/') != 0){
						filename = '/' + filename;
					}
				}
				
				
				file = Paths.get(filename); // create the path from the filename
				
				// Configure content type 
				contentType = fileExtension.equals("txt") ? "text/plain" : "text/html";
			}
			else if (fileExtension != null && fileExtension.equals("ico")){
				/*
				 * supposed to handle the favicon image but this doesnt
				 * seem to be working...  
				 */
				File f = new File(filename);
				out.println(httpVersion + ' ' + 200 + " OK");
				out.println("Content-Length: " + f.length());
				out.println("Content-Type: application/image");
				out.println(f);
			}
			else if (filename != null){
				/*
				 * handle folder contents here
				 */
				
				if (!filename.equals("") && filename.charAt(filename.length() - 1) == '/'){
					filename = filename.substring(0, filename.length()-1);
				}
				
				String workingDir = System.getProperty("user.dir");
				
				filename = filename.equals("") ? workingDir : filename;
				
				String filenameBackslash = filename.toString().replace('/', '\\');
				
				/*
				 * If the request is for something outside the working dir, 
				 * then quit service. 
				 */
				String tempWorkingDir, tempFilename;
				tempWorkingDir = (workingDir.indexOf('/') == 0) ? workingDir.substring(1) : workingDir;
				tempFilename = (filename.indexOf('/') == 0) ? filename.substring(1) : filename;
				tempWorkingDir = tempWorkingDir.toLowerCase();
				tempFilename = tempFilename.toLowerCase();
				
				if (filenameBackslash.indexOf(workingDir) < 0 && tempFilename.indexOf(tempWorkingDir) < 0){
					
					out.print(httpVersion + ' ' + 200 + " OK\n");
					out.print("Content-Length: 25\n");
					out.print("Content-Type: text/plain\n\n");
					out.print("Restricted area. Goodbye.");
					sock.close();
					return;
				}
				
				if (filename.indexOf('/') != 0){
					filename = '/' + filename;
				}
				
				File f = new File(filename);
				
				/*
				 * This opens and constructs the HTML for our subdirectory views. 
				 */
				File [] fileList = f.listFiles();
				ArrayList <String> lines = new ArrayList<String>();
				String s;
				String title = "<pre> <h1>Index of " + filename + "<h1>";
				String parentLink = "<a href=\"/\">Parent Directory</a> <br>";
				
				if (fileList != null) {
					for (File entry : fileList){
						if (entry.isDirectory()){
								String link;
							
								s = entry.toString().replace('\\', '/');
								
								if (s.indexOf('/') == 0){
									link = "<a href=\"" + s + "/\">" + s + "</a> <br>";
								}
								else{
									 link = "<a href=\"/" + s + "/\">" + s + "</a> <br>";
								}
					
								lines.add(link);
						}
						else if (entry.isFile()){
							String link;
							s = entry.toString().replace('\\', '/');
							s = (s.charAt(s.length() - 1) == '/') ? s.substring(0, s.length()-2) : s;
							
							if (s.indexOf('/') == 0){
								link = "<a href=\"" + s + "\">" + s + "</a> <br>";
							}
							else{
								link = "<a href=\"/" + s + "\">" + s + "</a> <br>";
							}
							lines.add(link);
						}
					}
					/*
					 * get size for the directory, plus the extra page HTML
					 */
					for (String l : lines){
						fileSize += l.length() + 1;
					}
					fileSize += title.length() + parentLink.length();
					
					/*
					 * Now send the header and the data
					 */
					out.print(httpVersion + ' ' + 200 + " OK\n");
					out.print("Content-Length: " + fileSize + '\n');
					out.print("Content-Type: text/html\n\n");
					out.print(title);
					out.print(parentLink);
					for (String l : lines){
						out.print(l);
					}
				}
			}


			/*
			 * Get the length of the file, send over header and data
			 * 
			 * This will NOT run unless a .txt or .html extension was recieved in the request
			 */
			Charset charset = Charset.forName("US-ASCII");
			
			if (file != null && fileExtension != null && !fileExtension.equals("ico")){
				try{
					assert(fileSize == 0);
					
					
					BufferedReader fileReader = Files.newBufferedReader(file, charset);
				
					String line = null;
					line = fileReader.readLine();
					
					while (line != null){
						
						// add +1 to fileSize each time because line.length()
						// does not include the new line character... Interesting...
						fileSize += line.length() + 1; 
						line = fileReader.readLine();
						
					}
					fileReader.close();
				} catch (IOException e){
					System.out.println("IOException, file not found");
					System.out.println("filename: " + filename);
				}
				
				/*
				 * Send over the header information
				 */
				out.print(httpVersion + ' ' + 200 + " OK\n");
				out.print("Content-Length: " + fileSize + '\n');
				out.print("Content-Type: " + contentType + "\n\n");
				System.out.println("Sent file: " + filename);
				
				/*
				 * Send over the file data
				 */
				try{
					BufferedReader fileReader = Files.newBufferedReader(file, charset);
					
					String line = null;
					line = fileReader.readLine();
					
					while (line != null){
						if (contentType.equals("text/plain")){
							out.print(line + '\n');
							line = fileReader.readLine();
						}
						else{
							out.print(line);
							line = fileReader.readLine();
						}
					}
					fileReader.close();
				} catch (IOException e){
					System.out.println("IOException, file not found");
				}
			}
			sock.close();
		} catch (IOException x){
			System.out.println("Server read error");
			x.printStackTrace();
		}
		sock.close();
		} catch (IOException ioe) {System.out.println(ioe);}
	}
}




/*
 * main thread for the server. It sits and waits for a connection from a client.
 * Once it gets a connection it creates a worker who will handle it. 
 */
public class MyWebServer {
	public static boolean controlSwitch = true;
	public static boolean JOKE_MODE = true;
	public static boolean MAINT_MODE = false;
	public static String [] Jokes = new String [5];
	public static String [] Proverbs = new String [5];

	public static void main(String a[]) throws IOException{
		int q_len = 6;
		int port = 2540;
		Socket sock;
		ServerSocket servsock = new ServerSocket(port, q_len);
		
		System.out.println("Nick Groos' WebServer starting up, listening at port " + port + ". \n");
		
		while (controlSwitch){
			sock = servsock.accept(); // continually wait and accept any client connections
			
			/*
			 * if you make it to here, that means we have connected to a client,
			 * and we create a worker to handle it
			 */
			if (controlSwitch) new WebWorker(sock).start(); 
			/*
			 * if this is uncommented you get some weird shutdown behavior... 
			 * Does not shutdown normally...
			 */
			 //try{Thread.sleep(10000);} catch(InterruptedException ex) {}
		}
		servsock.close();
	}
}


