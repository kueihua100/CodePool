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

#if 1
    for (auto& it: testMap) {
        std::cout << it.first << "=" << it.second << '\n';
        std::cout << "use_count()=" << it.second.use_count() << '\n';
        it.second.reset();
        std::cout << "use_count(11)=" << it.second.use_count() << '\n';
        if (it.second == nullptr)
            std::cout << it.first << "=" << it.second << '\n';
    }
#else
    std::cout << "AAA=" << testMap[std::string("AAA")] << '\n';
    std::cout << "BBB=" << testMap[std::string("BBB")] << '\n';
    std::cout << "CCC=" << testMap[std::string("CCC")] << '\n';
    std::cout << "use_count(AAA)=" << testMap[std::string("AAA")].use_count() << '\n';
    std::cout << "use_count(BBB)=" << testMap[std::string("BBB")].use_count() << '\n';
    std::cout << "use_count(CCC)=" << testMap[std::string("CCC")].use_count() << '\n';
    testMap.erase(std::string("AAA"));
    testMap.erase(std::string("BBB"));
    testMap.erase(std::string("CCC"));
    std::cout << "use_count(AAA11)=" << testMap[std::string("AAA")].use_count() << '\n';
    std::cout << "use_count(BBB11)=" << testMap[std::string("BBB")].use_count() << '\n';
    std::cout << "use_count(CCC11)=" << testMap[std::string("CCC")].use_count() << '\n';
#endif
    std::cout << "start clear map-------------------------------------" << '\n';
    testMap.clear();

    std::cout << "End clear map-------------------------------------" << '\n';

}