PACKER=/Library/Java/JavaVirtualMachines/jdk1.8.0_101.jdk/Contents/Home/bin/javapackager
NAME=jjamppong
MAIN=jjamppong.core
$PACKER -deploy -native dmg -outdir package -outfile $NAME -srcdir . -srcfiles target/project.jar -appclass $MAIN -name "$NAME" -title "$NAME"

