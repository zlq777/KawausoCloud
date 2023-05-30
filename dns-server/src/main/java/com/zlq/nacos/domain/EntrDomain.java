package com.zlq.nacos.domain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * EntrDomain entrance-service实例的ip,第二层映射
 *
 * @author zlq
 * @version 2023/05/24 13:50
 **/
public class EntrDomain {
    private ArrayList<String> arrayList = new ArrayList<>();
    private int tot = 0;

    public ArrayList<String> getArrayList() {
        return arrayList;
    }

    public void setArrayList(ArrayList<String> arrayList) {
        this.arrayList = arrayList;
    }

    synchronized public List<String> getIp(int num) {
        ArrayList<String> list = new ArrayList<>();
        int size = arrayList.size();
        if (num <= 0) num = 1;
        else if (num > arrayList.size()) num = arrayList.size();
        for (int i = tot; i < tot + num; i++) {
            int index = i % size;
            list.add(arrayList.get(index));
        }
        tot += num;
        tot %= size;
        return list;
    }
}
