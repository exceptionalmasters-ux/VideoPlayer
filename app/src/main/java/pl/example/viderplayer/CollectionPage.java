package pl.example.viderplayer;

/** Keep the native grid and decoded posters bounded without truncating the saved collection. */
public final class CollectionPage {
    public final int total,size,selected,first,end,number,pages;
    public CollectionPage(int total,int selected,int size){
        this.total=Math.max(0,total);this.size=Math.max(1,size);
        this.selected=this.total==0?0:Math.max(0,Math.min(selected,this.total-1));
        first=this.selected/this.size*this.size;end=Math.min(this.total,first+this.size);
        pages=Math.max(1,(this.total+this.size-1)/this.size);number=first/this.size+1;
    }
    public int move(int delta){return Math.max(0,Math.min(pages-1,number-1+delta))*size;}
    public int localSelection(){return Math.max(0,selected-first);}
}
