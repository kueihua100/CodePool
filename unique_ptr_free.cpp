#include <iomanip>
#include <iostream>
#include <memory>
#include <utility>
 
class AAA {
  public :
    AAA() {std::cout << "AAA()\n";}
    ~AAA() {std::cout << "~AAA()\n";}

};

class BBB {
  public :
    BBB() {
        std::cout << "BBB()\n";
        mAAAPtr = std::make_unique<AAA>();
    }
    ~BBB() {std::cout << "~BBB()\n";}
    std::unique_ptr<AAA> mAAAPtr;
};
 

int main()
{
    {
        std::cout << "CASE 1:\n";
        BBB* pBBB = new BBB();
        std::cout << "delete BBB ptr\n";
        delete pBBB;
        pBBB = nullptr;
    }
    {
        std::cout << "CASE 2:\n";
        std::unique_ptr<BBB> pBBBPtr = std::make_unique<BBB>();
        std::cout << "exit BBB scope\n";
    }
    std::cout << "do you see me??\n";
}