#!/bin/bash

echo "Re-building with target Java 7 (such that the compiled .class files will be compatible with as many JVMs as possible)..."

cd src

# build build build!
javac -encoding utf8 -d ../bin -bootclasspath ../other/java7_rt.jar -source 1.7 -target 1.7 @sourcefiles.list

cd ..



echo "Creating the release file assSecretary.zip..."

mkdir release

cd release

mkdir assSecretary

# copy the main files
cp -R ../bin assSecretary
cp ../UNLICENSE assSecretary
cp ../README.md assSecretary
cp ../run.sh assSecretary
cp ../run.bat assSecretary

# convert \n to \r\n for the Windows files!
cd assSecretary
awk 1 ORS='\r\n' run.bat > rn
mv rn run.bat
cd ..

# create a version tag right in the zip file
cd assSecretary
version=$(./run.sh --version_for_zip)
echo "$version" > "$version"
cd ..

# zip it all up
zip -rq assSecretary.zip assSecretary

mv assSecretary.zip ..

cd ..
rm -rf release

echo "The file assSecretary.zip has been created in $(pwd)"
