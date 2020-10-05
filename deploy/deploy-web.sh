#!/usr/bin/env bash

echo "[i] updating repo"
cd /data/project/wdumps/wdumper
git pull

echo "[i] scaling deployment to 2 replicas"
pod=$(kubectl get pod -o name -l name=wdumps)
kubectl scale --replicas=2 deployment wdumps

echo "[i] waiting for scaling to complete"
while kubectl get po -l name=wdumps | grep -q "0/1"; do
	sleep 1;
done

echo "[i] killing outdated container"
kubectl delete $pod 

echo "[i] scaling deployment down"
kubectl scale --replicas=1 deployment wdumps
