// "Pull method 'foo' up and make it abstract" "true"
public class Test{
    void main(){
        new Int(){
            @Overr<caret>ide
            void foo(){

            }
        };
    }
}

class Int {}