#!/bin/sh
originalfilename=$1
newfilename=$2
basedir=$3
existinglistfile=$4
cd "$basedir"

if [ -s "originalfilename" ]; then
    col_list=`cat "$existinglistfile"`
    csvcut -c $col_list "$originalfilename" > "$newfilename"
else
    cp "$originalfilename" "$newfilename"
fi
