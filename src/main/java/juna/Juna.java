package juna;

public class Juna {

    private final String servername;
    private final String fromEmail;
    
    public Juna(String servername, String fromEmail) {
        this.servername = servername;
        this.fromEmail = fromEmail;
    }

    public String getServername() {
        return servername;
    }

    public String getFromEmail() {
        return fromEmail;
    }
}
