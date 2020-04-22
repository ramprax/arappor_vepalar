JAR_FILES=lib/barcodes-7.0.2.jar:lib/font-asian-7.0.2.jar:lib/forms-7.0.2.jar:lib/hyph-7.0.2.jar:lib/io-7.0.2.jar:lib/itextpdf-5.0.6.jar:lib/kernel-7.0.2.jar:lib/layout-7.0.2.jar:lib/pdfa-7.0.2.jar:lib/sign-7.0.2.jar:lib/slf4j-api-1.7.13.jar
SRC_PATH=src
BIN_PATH=bin
SRC_FILES=`find $SRC_PATH -name *.java -type f | xargs`
javac -classpath JAR_FILES -d bin -sourcepath src $SRC_FILES

