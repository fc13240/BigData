LIB_PATH=.
for i in ../lib/*.jar; do
LIB_PATH=${LIB_PATH},$i
done
LIB_PATH=${LIB_PATH:0,2}
#echo $LIB_PATH
/usr/local/spark/bin/spark-submit --class com.wyd.BigData.App --files /usr/local/spark/conf/hive-site.xml --jars $LIB_PATH --master spark://Master:7077 ./BigData.jar >> stdout.log 2>&1 &

echo $! > bigdata.pid