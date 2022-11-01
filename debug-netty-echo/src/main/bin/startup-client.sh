APP_HOME=${PWD}/..
MAIN_CLASS_NAME="com.gupaoedu.vip.netty.chat.client.ChatClient"

JVM_ARGS="-DlogDir=$APP_HOME/logs"
if [ -r app.vmoptions ];then
JVM_ARGS="$JVM_ARGS `tr '\n' ' ' < app.vmoptions`"
fi

process_Id=`/usr/sbin/lsof -i tcp:$PORT|awk '{print $2}'|sed '/PID/d'`


PATH="./classes"
CLASSPATH=$APP_HOME/$PATH
for i in $APP_HOME/lib/*.jar;do
CLASSPATH="$i:$CLASSPATH"
done

export CLASSPATH


#echo "java_home is $JAVA_HOME"
#echo "APP_HOME is $APP_HOME \n"
#echo "CLASSPATH is $CLASSPATH \n"
#echo "JVM_ARGS is $JVM_ARGS "
#echo "MAIN_CLASS_NAME is $MAIN_CLASS_NAME"
#echo "process_Id is $process_Id \n"
#echo "$PORT \n"
#echo "$JAVA_HOME/bin/java $JVM_ARGS -classpath $CLASSPATH $MAIN_CLASS_NAME $PORT &"


start(){
    printf 'RenderServer is starting...\n'
    if [ $process_Id ];then
       kill -9 $process_Id
       sleep 1
    fi 

   $JAVA_HOME/bin/java $JVM_ARGS -classpath $CLASSPATH $MAIN_CLASS_NAME $PORT &
}

restart(){
    printf 'RenderServer is restart...\n'
    if [ $process_Id ];then
       kill -9 $process_Id
       sleep 1
    fi 

   $JAVA_HOME/bin/java $JVM_ARGS -classpath $CLASSPATH $MAIN_CLASS_NAME $PORT &
}

stop (){
   printf 'RenderServer is stoping...\n'
   if [ $process_Id ];then
     kill -9 $process_Id 
   fi 
}
 



case "$1" in
start)
  start
  ;;
restart)
  restart
  ;;
stop)
  stop
;;
*)
  printf 'Usage:%s {start|restart|stop}\n'
  printf 'app.vmoptions is configuration for  JVM \n '
  exit 1
  ;;
esac
