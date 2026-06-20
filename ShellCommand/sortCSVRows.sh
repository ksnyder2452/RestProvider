#!/bin/sh
original_file=$1
output_file=$2
if [ -s "$original_file" ]; then
    csvsort -y 0 -I "$original_file" > "$output_file"
else
    cp "$original_file" "$output_file"
fi
