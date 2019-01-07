package com.flowkode.hubldap.data;

public class PagedResponse {

    private final int skip;

    private final int top;

    private final int total;

    public PagedResponse(int skip, int top, int total) {
        this.skip = skip;
        this.top = top;
        this.total = total;
    }

    public int getSkip() {
        return skip;
    }

    public int getTop() {
        return top;
    }

    public int getTotal() {
        return total;
    }
}
