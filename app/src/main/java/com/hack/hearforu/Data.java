package com.hack.hearforu;

public class Data {
    public String covid;
    public boolean fever;
    public String fevertemperture;
    public int age;
    public boolean cough;
    public String covidAudioUrl;

    public Data(String covid, boolean fever, String fevertemperture,
                     int age, boolean cough, String covidAudioUrl){

        this.covid = covid;
        this.fever = fever;
        this.fevertemperture = fevertemperture;
        this.age = age;
        this.cough = cough;
        this.covidAudioUrl = covidAudioUrl;

    }

}
