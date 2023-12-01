#include <iostream>

class IFilter {
  public:
    IFilter(){};
    IFilter(int in);
    virtual ~IFilter();
    virtual void start() = 0;
  private:
    int type;
};
IFilter::IFilter(int in) {
    std::cout << "IFilter(IIII) \n";
    type = in;
}
IFilter::~IFilter() {
    std::cout << "~IFilter(IIII) \n";
}


class Filter : public IFilter {
  public:
    Filter(){};
    Filter(int in);
    virtual ~Filter();
    virtual void start() override;
  private:
    int type;
};
Filter::Filter(int in) {
    std::cout << "Filter(D111) \n";
    type = in;
}
Filter::~Filter() {
    std::cout << "~Filter(D111) \n";
}
void Filter::start() {
    std::cout << "start(D111):" << "type=" << type <<"\n";
}

class AFilter: public Filter {
  public:
    AFilter(){};
    AFilter(int in);
    virtual void start() override;
  private:
    ~AFilter();
    int type;    
};
AFilter::AFilter(int in) {
    std::cout << "AFilter(D222) \n";
    type = in;
}
AFilter::~AFilter() {
    std::cout << "~AFilter(D222) \n";
}
void AFilter::start() {
    std::cout << "start(D222):" << "type=" << type <<"\n";
}

class BFilter: public Filter {
  public:
    BFilter(){};
    BFilter(int in);
    virtual void start() override;
  private:
    ~BFilter();
    int type;    
};
BFilter::BFilter(int in) {
    std::cout << "BFilter(D222) \n";
    type = in;
}
BFilter::~BFilter() {
    std::cout << "~BFilter(D222) \n";
}
void BFilter::start() {
    std::cout << "start(D222):" << "type=" << type <<"\n";
}

int main()
{
    Filter* aFilter = new AFilter(222);
    aFilter->start();
    
    Filter* bFilter = new BFilter(222);
    bFilter->start();
    
    Filter* filter = new Filter(111);
    filter->start();
    delete aFilter;
    delete bFilter;
    delete filter;
}
