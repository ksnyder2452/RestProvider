#!/bin/sh
crontab_time=$1
crontab_command=$2
(crontab -l ; echo "$crontab_time $crontab_command") | sort - | uniq - | crontab -
