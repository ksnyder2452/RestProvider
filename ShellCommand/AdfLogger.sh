#!/bin/sh
runId=$1
analyticsWorkspaceId=$2
resultFolder="$3"
resultFile="$resultFolder"$4

az monitor log-analytics query -w $analyticsWorkspaceId --analytics-query "AppTraces | where OperationName <> '' | join kind=leftouter ( AppTraces | where OperationName == '' | project OperationRightId=tostring(Properties.OperationId), RightMessage=Message, props=Properties ) on \$left.OperationId == \$right.OperationRightId and \$left.Message == \$right.RightMessage | project TimeGenerated, Logger=props.Logger, Level=Properties.LogLevel, Message, RunId=props.RunId, FileName=props.FileName, FileEntity=props.FileEntity, Source=props.Source, Action=props.Action, CountSuccess=props.CountSuccess, CountFailed=props.CountFailed, CountTotal=props.CountTotal, ExecutionTime=props.ExecutionTime | where Logger in ('PdrAdfLogger') | where Action in ('SendRiskToSFHC') | where Level in ('Error') | where RunId == '$runId' | order by TimeGenerated" > "$resultFile"
