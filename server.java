import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.lang.Runtime;
import java.lang.String;
import java.lang.Runnable;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.File; 
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.lang.String;
import java.util.concurrent.Semaphore;

//--------------------------------------------------------------
//  This program uses basic HTTP commands GET, HEAD, POST
//  and is multi threaded, up to 5 threads. 
//
//  Henry Kung  500386346
//
//  How to compile:     javac server.java
//  How to run:         java  server -p <port_number> -d <optional>
//
//
//  Last Updated: Oct. 2, 2014
//-------------------------------------------------------------

public class server 
{
    public static String doc_root;
    public static String contenttype[];
    public static String host;
    public static String http_type;
    public static int port;
    public static ServerSocket serversocket;
    public static int requested_filetype;
    public static int content_length;
    public static int pool_size;
    public static int queue_size;
    public static Socket connectionsocket;
    static ArrayList<thread> pool_array;  
    static ArrayList<thread> queue_array; 
    private static Semaphore write_lock;

    // Help message when running the server
    public static void usage()
    {
        System.err.println("Usage server.java:");
        System.err.println("-h, --help Show this help message");
        System.err.println("-p, --port Port number");
        System.err.println("-d, --print addition information");
        System.exit(0);
    }

    public static void main (String[] args) throws Exception
    {

        port=0;
        doc_root=null;
        contenttype=null;
        http_type=null;
        pool_size = 0;
        queue_size= 0;
        thread temp;

        // Check arguements for -p and -d
        //System.out.println(args.length);
        if(args.length < 2) {
            usage();
        }
        for(int i = 0; i < args.length; i++) {
            //System.out.println(args[i]);
            if(args[i].equals("-p")) {
                i=i+1;
                port = Integer.parseInt(args[i]);
                //System.out.println(port);
            } 
            // add -d later on for printing out log info
            else if(args[i].equals("-d")) {
                System.out.println("Print log");
            } else {
                usage();
            }
        }

        BufferedReader br = new BufferedReader(new FileReader("myhttpd2.conf"));
        String line;
        while((line = br.readLine()) != null) {
            //System.out.println(line);
            String[] words = line.split("\\s+");
            if(doc_root == null) {
                words[1] = words[1].replace("[","");
                words[1] = words[1].replace("]","");
                //System.out.println(words[1]);
                doc_root = words[1];
                http_type = words[0];
                //System.out.println(doc_root);
                //System.out.println(http_type);
            }

            // reads second line for acceptable file types
            line = br.readLine();
            words = line.split("\\s+");
            if(contenttype == null) {
                contenttype = words;
                //System.out.println(contenttype);
            }

            // gets the size for the pool
            line = br.readLine();
            words = line.split("\\s+");
            pool_size = Integer.parseInt(words[1]);
            pool_array = new ArrayList<thread>(pool_size);
            //System.out.println(pool_size);

            // gets the size for the queue
            line = br.readLine();
            words = line.split("\\s+");
            queue_size = Integer.parseInt(words[1]);
            queue_array = new ArrayList<thread>(queue_size);
            //System.out.println(queue_size);

            //Checking to split properly
            /*
               for(int i = 0; i < words.length; i++) {
               System.out.println(words[i]);
               }
               */
        }

        // Instantiate semaphores
        //System.out.println(pool_array.size());
        write_lock = new Semaphore(1, true);
        

        // Initializes Socket
        try {
            System.out.println("Trying to bind server to specified port number: "+Integer.toString(port)+"\n");
            //Create socket for server with port #
            serversocket = new ServerSocket(port);
            System.out.println("Binding complete\n");
        } catch(Exception e) {
            System.err.println("Failed to bind server to specified port\n");
        }

        while(true) {
            System.out.println("\nReady, waiting for connection\n");
            try {
                //Accept connection from client
                connectionsocket = serversocket.accept();
                if(connectionsocket.isConnected()) {
                    if (pool_size == pool_array.size()) {
                        System.out.println("\nPool is full. Moving client request to queue");
                        thread t = new thread(connectionsocket, doc_root, http_type, contenttype, requested_filetype, write_lock);
                        queue_array.add(t);
                        clean_pool();
                    } else {
                        thread t = new thread(connectionsocket, doc_root, http_type, contenttype, requested_filetype, write_lock);
                        pool_array.add(t);
                        t.start();
                    }
                }
            } catch (Exception e) {
                System.err.println("Something went wrong");
            }
            // check if pool_array has space move element from queue to pool
           if (!queue_array.isEmpty()) 
           {
               System.out.println("moving thread from queue to pool");
               for(int i = 0; i < queue_array.size(); i++)
               {
                   if(pool_array.size() < pool_size)
                   {
                       pool_array.add(queue_array.get(i)); 
                       queue_array.remove(i); pool_array.get(pool_array.size() -1).start(); }
               }
           }
        }
    }
    public static void clean_pool()
    {
        System.out.println("initializing pool clean up");
        ArrayList<thread> temp = new ArrayList<thread>(pool_size);
        for(int i = 0; i < pool_size; i++)
        {
            if(pool_array.get(i).get_state() == 1 )
            {
                pool_array.set(i,null);
                //System.out.println(pool_array.toString());
            } else {
                temp.add(pool_array.get(i));

            }
        }
        pool_array = temp;
        //System.out.println(pool_array.toString());
        //System.out.println(pool_array.size());
        //pool_array.clear();
        //System.out.println(pool_array.size());

    }
}

class thread implements Runnable
{
    public Socket connectionsocket;
    public String doc_root;
    public String http_type;
    public String contenttype[];
    public int requested_filetype;
    private Thread  t;
    private Semaphore write_lock;
    private int done;

    public thread(Socket socket, String root, String type, String content[], int filetype, Semaphore write)
    {
        connectionsocket = socket;
        doc_root = root;
        http_type = type;
        contenttype = content;
        requested_filetype = filetype;
        write_lock = write;
        done = 0;
    }
    public void start()
    {
        t = new Thread(this, "thread");     
        t.start();
    }
    public void kill()
    {
        try{
            t.join();
        } catch (InterruptedException t) {}
    }

    public void run() 
    {
        //Obtains IP address from client
        InetAddress client = connectionsocket.getInetAddress();
        System.out.println(client.getHostName()+" Client connected\n");
        //Create input buffer to read  http request
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(connectionsocket.getInputStream()));
            //Create output stream from server to client
            DataOutputStream output = new DataOutputStream(connectionsocket.getOutputStream());
            // Test for multithreading
            //Thread.sleep(10000);
            http_handler(input, output);
        } catch (IOException e) {
            done = 1;
        } 

    }

    // Handler handles server and client recieve and send

    private void http_handler(BufferedReader input, DataOutputStream output) {
        int method=0;
        String http;            // String holds the whole content
        String path=doc_root;   // Holds the path to server?
        String file;            // Holds the file name
        String user_agent;      // what user_agent
        long content_length;   // Length of content
        File f; 

        try{
            // Handles 3 types of request, POST, HEAD, GET
            // GET /index.html HTTP/1.0
            // POST /index.html HTTP/1.0
            // HEAD /index.html HTTP/1.0

            String tmp = input.readLine();
            tmp.toUpperCase();
            System.out.println(tmp);
            String tmp2 = new String(tmp);

            // Sets which kind of method is being called
            if (tmp.startsWith("GET")) {
                System.out.println("GET method was invoked");
                method=1;
            }
            if (tmp.startsWith("HEAD")) {
                System.out.println("HEAD method was invoked");
                method=2;
            }
            if (tmp.startsWith("POST")) {
                System.out.println("POST method was invoked");
                method=3;
            }
            //System.out.println(method);

            // If method is unsupported
            if(method==0) {
                try {
                    System.err.println("method not supported error 501");
                    // Creates header with error code 501
                    output.writeBytes(construct_http_header(501,0,0));
                    output.close();
                } catch (Exception e) {
                    // Was unable to send 501 error
                    System.err.println("Unable to send 501 error");
                }
                done = 1;
            }

            int start=0;
            int end=0;

            for(int i =0; i < tmp2.length(); i++) {
                if (tmp2.charAt(i) == ' ' && start != 0) {
                    end = i;
                    break;
                }
                if (tmp2.charAt(i) == ' ' && start == 0 ) {
                    start = i;
                }
            }
            // Path of file 
            path = path + "/"+tmp2.substring(start+2, end);

        } catch (Exception e) {
            System.err.println("HTTP handler error occured");
            done = 1;
        }

        if (method ==1) {
            System.out.println("Client requested link: "+ path);
        }
        if (method == 3) {
            System.out.println("Client wishes to send "+ path);
        }

        FileInputStream requestedfile = null;
        f = new File(path);
        content_length = f.length();
        System.out.println("Size: " + content_length);

        if (method == 1) {
            f = new File(path);
            // Check is file exists
            if(f.exists()) {
                if (f.canRead()) {
                    try {
                        // try to open file
                        requestedfile = new FileInputStream(path);
                    } catch (Exception e) { 
                        try {
                            //Create header with error code 501
                            //if could not open file send 403
                            System.err.println("Could not open file error 403");
                            output.writeBytes(construct_http_header(403,0,0));
                            output.close();
                            input.close();
                        } catch (Exception t) {
                            // Was unable to send 403
                            System.err.println("Unable to send 403 error");
                        }
                        done = 1;
                    }
                } else {
                    try {
                        //Create header with error code 501
                        //if could not open file send 403
                        System.err.println("Could not open file error 403");
                        output.writeBytes(construct_http_header(403,0,0));
                        output.close();
                        input.close();
                    } catch (Exception t) {
                        // Was unable to send 403
                        System.err.println("Unable to send 403 error");
                    }
                    done = 1;
                }
            } else {
                try {
                    System.err.println("Could not open file error 404");
                    output.writeBytes(construct_http_header(404,0,0));
                    input.close();
                    output.close();
                    done = 1;
                } catch (Exception t) {
                    System.err.println("Unable to send 404");
                    done = 1;
                }

            }
        }
        try {
            // This is going to find the file type requested
            int valid_type= 0;

            // This loop checks to see if file requested is supported
            for(int i = 0;i < contenttype.length; i++) {
                if(path.endsWith("."+contenttype[i])){
                    valid_type = 1;
                    requested_filetype=i;
                } 
                //System.out.println(contenttype[i]);
            }
            try {
                if (valid_type == 1) {
                    System.out.println("Everything is OKAY. Response 200");
                    output.writeBytes(construct_http_header(200,requested_filetype, content_length));
                } else {
                    //If file type was not supported send 400
                    System.err.println("Improper file type request");
                    output.writeBytes(construct_http_header(400,5,0));
                    output.close();
                    input.close();
                    done = 1;
                }
            } catch(Exception t) {
                System.err.println("Was not able to send 200/400");
                done = 1;
            }

            // If GET was requested
            if (method==1) {
                //Read file from filestream
                //Print file to client byte by byte
                System.out.println("Sending Bytes to Client\n");
                while(true) {
                    int b = requestedfile.read();
                    System.out.print(b);
                    if ( b ==-1)  {
                        output.writeChars("\r\n");
                        break;
                    } else {
                        //output.writeInt(b);
                        output.write(b);
                    }
                }
                done = 1;
            }
            // If POST was requested
            if (method ==3 ) {
                write_lock.acquire();
                String tmp3 = input.readLine();
                int start=0;
                for(int i = 0;i < tmp3.length(); i++) {
                    if (tmp3.charAt(i) == ' ' && start == 0 ) {
                        start = i;
                        break;
                    }
                }
                // tmp3 is the content-length of post (in bytes)
                tmp3 = tmp3.substring(start+1,tmp3.length());
                //System.out.println(tmp3);

                input.readLine();
                // reads from client file to write
                File new_file = new File(path);
                PrintWriter writer = new PrintWriter(new_file);
                int content; 
                for (int i =0; i < Integer.parseInt(tmp3); i++ ) {
                    content = input.read();
                    writer.write(content);
                }
                writer.close();
                output.writeBytes(construct_http_header(201,0,0)); 
                output.close();
                input.close();
                write_lock.release();
                done = 1;
            }
            // closes file and output stream
            input.close();
            output.close();
            requestedfile.close();
            done = 1;
        } catch (Exception e) {done = 1;}
    }

    // This method makes the HTTP header for responses
    private String construct_http_header(int return_code, int file_type, long content_length) {
        String header = new String(http_type);
        switch (return_code) {
            case 200:
                header = header + " 200 OK";
                break;
            case 201:
                header = header + " 201 POST OK";
                break;
            case 400:
                header = header + " 400 BAD REQUEST";
                break;
            case 403:
                header = header + " 403 NO READ PERMISSION";
                break;
            case 404:
                header = header + " 404 FILE NOT FOUND";
                break;
            case 501:
                header = header + " 501 REQUEST NOT IMPLEMENTED";
                break;
        }

        //System.out.println(http_type);

        //construct other header fields 
        header = header + "\r\n";
        header = header + "Server: CPS730\r\n";

        // Builds proper content type
        // If more file types is introduced should add in switch statement
        switch(file_type){
            case 0:
                header = header + "Content-Type: text/HTML\r\n";
                break;
            case 1:
                header = header + "Content-Type: text/html\r\n";
                break;
            case 2:
                header = header + "Content-Type: text/htm\r\n";
                break;
        }

        header = header + "Content-Length: " + content_length + "\r\n";
        header = header + "\r\n";
        return header;
    }

    public int get_state()
    {
        return done;
    }
}


