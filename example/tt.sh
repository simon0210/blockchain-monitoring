i=0;
while true 
do
  i=$[$i+1];
   echo "next $i";
   echo $(($i%4)) $(($i%2));
if [ $(($i%2)) -eq 0 ] ; then
	echo "Machine is giving ping response"
else
	echo "Machine is not pinging"
fi
done