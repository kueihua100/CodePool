#include <iostream>
#include <string>
#include <map>
#include <thread>

class AAA {
  public:
    AAA() { std::cout << "AAA()" << '\n'; }
    AAA(int i) { std::cout << "AAA() i=" << i <<'\n'; }
    ~AAA() { std::cout << "~AAA()" << '\n'; }
};
 
int main()
{
    std::map<std::string, std::shared_ptr<AAA>> testMap;
    {
        std::shared_ptr<AAA> aaa = std::make_shared<AAA>(1);
        testMap[std::string("AAA")] = aaa;
        std::shared_ptr<AAA> bbb = std::make_shared<AAA>(2);
        testMap[std::string("BBB")] = bbb;
        std::shared_ptr<AAA> ccc = std::make_shared<AAA>(3);
        testMap[std::string("CCC")] = ccc;
        std::cout << "aaa.use_count()=" << aaa.use_count() << '\n';
        std::cout << "bbb.use_count()=" << bbb.use_count() << '\n';
        std::cout << "ccc.use_count()=" << ccc.use_count() << '\n';
    }
    
    for (auto& it: testMap) {
        std::cout << it.first << "=" << it.second << '\n';
        std::cout << "use_count()=" << it.second.use_count() << '\n';
        it.second.reset();
        std::cout << "use_count(11)=" << it.second.use_count() << '\n';
        if (it.second == nullptr)
            std::cout << it.first << "=" << it.second << '\n';
    }
    std::cout << "start clear map-------------------------------------" << '\n';
    testMap.clear();
  
    std::cout << "End clear map-------------------------------------" << '\n';
    
}