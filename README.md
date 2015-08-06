# ENCODE-X

This repo uses ADAM genomics processing engine as the base.
    
    git clone https://github.com/nikhilRP/encode-x.git
    cd encode-x
    mvn clean package -DskipTests
    
start the server using

    bin/encodex-submit
    
Access the data in the browser using following format
    
    http://<server:port>/reads/<file>
    or
    http://<server:port>/reads/<file>?ref=<chr>&start=<start>&end=<end>

