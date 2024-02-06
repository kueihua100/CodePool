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
 
class CCC {
  public :
    CCC() {
        std::cout << "CCC()\n";
    }
    ~CCC() {
        std::cout << "~CCC()\n";
    }
    AAA mAAA;
};

int main()
{
    {
        std::cout << "\nCASE 1:\n";
        BBB* pBBB = new BBB();
        std::cout << "delete BBB ptr\n";
        delete pBBB;
        pBBB = nullptr;
        std::cout << "CASE 1 Out\n\n";
    }
    {
        std::cout << "\nCASE 2 In:\n";
        std::unique_ptr<BBB> pBBBPtr = std::make_unique<BBB>();
        std::cout << "CASE 2 Out\n\n";
    }
    {
        std::cout << "\nCASE 3 In:\n";
        std::unique_ptr<BBB> pBBBPtr = std::make_unique<BBB>();
        pBBBPtr.reset(nullptr); // equals using: pBBBPtr = nullptr;
        std::cout << "CASE 3 Out\n\n";
    } 
    {
        std::cout << "\nCASE 4 In:\n";
        CCC ccc;
        std::cout << "CASE 4 Out\n\n";
    }
    std::cout << "do you see me??\n";
}
